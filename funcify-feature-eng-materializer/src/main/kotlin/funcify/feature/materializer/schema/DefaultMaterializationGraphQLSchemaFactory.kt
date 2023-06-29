package funcify.feature.materializer.schema

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.impl.CompositeSDLDefinitionImplementationStrategy
import funcify.feature.tools.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.service.MaterializationGraphQLWiringFactory
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.flatMapFailure
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.GraphQLError
import graphql.language.DirectiveDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.OperationTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.SchemaDefinition
import graphql.language.TypeName
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import kotlin.reflect.KClass

internal class DefaultMaterializationGraphQLSchemaFactory(
    private val jsonMapper: JsonMapper,
    private val scalarTypeRegistry: ScalarTypeRegistry,
    private val sdlDefinitionCreationContextFactory:
        SchematicVertexSDLDefinitionCreationContextFactory,
    private val sdlDefinitionImplementationStrategies:
        List<SchematicVertexSDLDefinitionImplementationStrategy>,
    private val materializationGraphQLWiringFactory: MaterializationGraphQLWiringFactory
) : MaterializationGraphQLSchemaFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLSchemaFactory>()

        private data class GraphQLSchemaBuildContext(
            val metamodelGraph: MetamodelGraph,
            val scalarTypeDefinitions: PersistentList<ScalarTypeDefinition> = persistentListOf(),
            val directiveDefinitions: PersistentList<DirectiveDefinition> = persistentListOf(),
            val implementingTypeDefinitions: PersistentList<ImplementingTypeDefinition<*>> =
                persistentListOf(),
            val runtimeWiringBuilder: RuntimeWiring.Builder = RuntimeWiring.newRuntimeWiring(),
            val typeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry()
        )
    }

    private val compositeSDLDefinitionImplementationStrategy:
        CompositeSDLDefinitionImplementationStrategy by lazy {
        CompositeSDLDefinitionImplementationStrategy(
            sdlDefinitionImplementationStrategies = sdlDefinitionImplementationStrategies
        )
    }

    override fun createGraphQLSchemaFromMetamodelGraph(
        metamodelGraph: MetamodelGraph
    ): Try<GraphQLSchema> {
        val firstVertexPathSupplier: () -> SchematicPath? = { ->
            metamodelGraph.pathBasedGraph.vertices
                .toOption()
                .filter { v -> v.size > 0 }
                .map { v -> v[0].path }
                .orNull()
        }
        logger.info(
            """create_graphql_schema_from_metamodel_graph: [ 
                |metamodel_graph: [ vertices.size: ${metamodelGraph.pathBasedGraph.vertices.size}, 
                |vertices[0].path: ${firstVertexPathSupplier.invoke()} 
                |] ]""".flatten()
        )
        if (logger.isDebugEnabled) {
            logSchematicVerticesToBeWiredIntoMaterializationGraphQLSchema(metamodelGraph)
        }
        return metamodelGraph.pathBasedGraph.vertices
            .stream()
            .sorted { sv1, sv2 -> sv1.path.compareTo(sv2.path) }
            .reduce(
                Try.attempt { createInitialContextWithExtendedScalars(metamodelGraph) },
                ::applyStrategyToSchematicVertexSDLDefinitionCreationContextAndSwitchToNextVertex,
                { sdlDefCreationCtx, _ -> sdlDefCreationCtx }
            )
            .map { ctx: SchematicVertexSDLDefinitionCreationContext<*> ->
                createBuildContextFromSDLDefinitionCreationContext(ctx)
            }
            .map { ctx: GraphQLSchemaBuildContext ->
                createTypeDefinitionRegistryInBuildContext(ctx)
            }
            .map { ctx: GraphQLSchemaBuildContext -> updateRuntimeWiringBuilderInBuildContext(ctx) }
            .flatMap { ctx: GraphQLSchemaBuildContext ->
                Try.attempt({
                        SchemaGenerator()
                            .makeExecutableSchema(
                                ctx.typeDefinitionRegistry,
                                ctx.runtimeWiringBuilder.build()
                            )
                    })
                    .mapFailure { t: Throwable ->
                        MaterializerException(
                            GRAPHQL_SCHEMA_CREATION_ERROR,
                            """error occurred when creating executable schema: 
                                |[ type: ${t::class.qualifiedName} ]
                                |""".flatten(),
                            t
                        )
                    }
            }
    }

    private fun logSchematicVerticesToBeWiredIntoMaterializationGraphQLSchema(
        metamodelGraph: MetamodelGraph
    ) {
        var counter: Int = 0
        val vertexSuperTypeNameExtractor: (Pair<SchematicPath, SchematicVertex>) -> String =
            { (_, v) ->
                v::class
                    .supertypes
                    .asSequence()
                    .filterIsInstance<KClass<*>>()
                    .firstOrNull()
                    .toOption()
                    .mapNotNull { kcls -> kcls.simpleName }
                    .getOrElse { v::class.simpleName ?: "<NA>" }
            }
        val vertexNameExtractor: (Pair<SchematicPath, SchematicVertex>) -> String = { (_, v) ->
            when (v) {
                is SourceRootVertex -> v.compositeContainerType.conventionalName.toString()
                is SourceJunctionVertex -> v.compositeAttribute.conventionalName.toString()
                is SourceLeafVertex -> v.compositeAttribute.conventionalName.toString()
                is ParameterJunctionVertex ->
                    v.compositeParameterAttribute.conventionalName.toString()
                is ParameterLeafVertex -> v.compositeParameterAttribute.conventionalName.toString()
                else -> "<NA>"
            }
        }
        metamodelGraph.pathBasedGraph.verticesByPath
            .streamPairs()
            .sorted { p1, p2 -> p1.first.compareTo(p2.first) }
            .forEach { pair: Pair<SchematicPath, SchematicVertex> ->
                val vertexName = vertexNameExtractor.invoke(pair)
                val vertexSuperTypeName = vertexSuperTypeNameExtractor.invoke(pair)
                logger.debug(
                    "[${counter++}]: [ path: ${pair.first}, vertex: [ type: $vertexSuperTypeName, name: $vertexName ]"
                )
            }
    }

    private fun applyStrategyToSchematicVertexSDLDefinitionCreationContextAndSwitchToNextVertex(
        creationContextUpdateAttempt: Try<SchematicVertexSDLDefinitionCreationContext<*>>,
        sv: SchematicVertex
    ): Try<SchematicVertexSDLDefinitionCreationContext<SchematicVertex>> {
        return creationContextUpdateAttempt
            .flatMap { ctx: SchematicVertexSDLDefinitionCreationContext<*> ->
                if (compositeSDLDefinitionImplementationStrategy.canBeAppliedToContext(ctx)) {
                    compositeSDLDefinitionImplementationStrategy.applyToContext(ctx)
                } else {
                    Try.failure(
                        MaterializerException(
                            GRAPHQL_SCHEMA_CREATION_ERROR,
                            "no strategy could be applied to context: [ context.path: ${ctx.path} ]"
                        )
                    )
                }
            }
            .map { ctx -> ctx.update { nextVertex(sv) } }
    }

    private fun createInitialContextWithExtendedScalars(
        metamodelGraph: MetamodelGraph
    ): SchematicVertexSDLDefinitionCreationContext<*> {
        return sdlDefinitionCreationContextFactory
            .createInitialContextForRootSchematicVertexSDLDefinition(
                metamodelGraph = metamodelGraph,
                scalarTypeDefinitions = scalarTypeRegistry.getAllScalarDefinitions()
            )
    }

    private fun createBuildContextFromSDLDefinitionCreationContext(
        sdlDefinitionCreationContext: SchematicVertexSDLDefinitionCreationContext<*>
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """create_build_context_from_sdl_definition_creation_context: 
               |[ sdl_definition_creation_context.
               |sdl_definitions_by_schematic_path.size: 
               |${sdlDefinitionCreationContext.sdlDefinitionsBySchematicPath.size} ]
               |""".flatten()
        )
        return GraphQLSchemaBuildContext(
            metamodelGraph = sdlDefinitionCreationContext.metamodelGraph,
            scalarTypeDefinitions =
                sdlDefinitionCreationContext.scalarTypeDefinitionsByName.values.toPersistentList(),
            directiveDefinitions =
                sdlDefinitionCreationContext.directiveDefinitionsByName.values.toPersistentList(),
            implementingTypeDefinitions =
                sdlDefinitionCreationContext.implementingTypeDefinitionsBySchematicPath.values
                    .toPersistentList()
        )
    }

    private fun createTypeDefinitionRegistryInBuildContext(
        buildContext: GraphQLSchemaBuildContext
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """create_type_definition_registry_in_build_context: 
                |[ build_context.implementing_type_definitions.size: 
                |${buildContext.implementingTypeDefinitions.size} 
                |]""".flatten()
        )
        val queryOperationDefinition: OperationTypeDefinition =
            createQueryOperationDefinitionFromSourceRootVertex(buildContext.metamodelGraph)
        val typeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry()
        typeDefinitionRegistry
            .add(
                SchemaDefinition.newSchemaDefinition()
                    .operationTypeDefinition(queryOperationDefinition)
                    .build()
            )
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("schema_definition"))
        typeDefinitionRegistry
            .addAll(buildContext.scalarTypeDefinitions)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("scalar_type_definitions"))
        typeDefinitionRegistry
            .addAll(buildContext.directiveDefinitions)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("directive_definitions"))
        typeDefinitionRegistry
            .addAll(buildContext.implementingTypeDefinitions)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("implementing_type_definitions"))
        return buildContext.copy(typeDefinitionRegistry = typeDefinitionRegistry)
    }

    private fun createQueryOperationDefinitionFromSourceRootVertex(
        metamodelGraph: MetamodelGraph
    ): OperationTypeDefinition {
        return when (
            val sourceRootVertex: SourceRootVertex? =
                metamodelGraph.pathBasedGraph.verticesByPath[SchematicPath.getRootPath()]
                    as? SourceRootVertex
        ) {
            null -> {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """root_source_vertex not found in metamodel_graph; 
                       |cannot create operation definition""".flatten()
                )
            }
            else -> {
                OperationTypeDefinition.newOperationTypeDefinition()
                    .name(
                        StandardNamingConventions.CAMEL_CASE.deriveName(
                                sourceRootVertex.compositeContainerType.conventionalName.toString()
                            )
                            .toString()
                    )
                    .typeName(
                        TypeName(
                            StandardNamingConventions.PASCAL_CASE.deriveName(
                                    sourceRootVertex.compositeContainerType.conventionalName
                                        .toString()
                                )
                                .toString()
                        )
                    )
                    .build()
            }
        }
    }

    private fun typeRegistryUpdateGraphQLErrorHandler(
        definitionTypeBeingAdded: String
    ): (GraphQLError) -> Unit {
        return { gqlError: GraphQLError ->
            val graphQLErrorMessageAttempt: Try<String> =
                Try.attempt { gqlError.toSpecification() }
                    .flatMap { specMap -> jsonMapper.fromKotlinObject(specMap).toJsonString() }
                    .flatMapFailure { t: Throwable ->
                        if (
                            gqlError.locations
                                .toOption()
                                .filter { locs -> locs.any { srcLoc -> srcLoc == null } }
                                .isDefined()
                        ) {
                            jsonMapper
                                .fromKotlinObject(
                                    mapOf(
                                        "errorType" to gqlError.errorType,
                                        "message" to gqlError.message,
                                        "path" to gqlError.path,
                                        "extensions" to gqlError.extensions
                                    )
                                )
                                .toJsonString()
                        } else {
                            Try.failure(t)
                        }
                    }
            if (graphQLErrorMessageAttempt.isFailure()) {
                val message = graphQLErrorMessageAttempt.getFailure().orNull()!!.message
                logger.error(
                    """type_registry_update_graphql_error_handler: [ status: failed ] 
                    |unable to serialize graphql_error into json: [ message: $message 
                    |]""".flatten()
                )
                throw MaterializerException(GRAPHQL_SCHEMA_CREATION_ERROR, gqlError.message)
            }
            throw MaterializerException(
                GRAPHQL_SCHEMA_CREATION_ERROR,
                """error reported when adding $definitionTypeBeingAdded 
                   |to type_definition_registry: [ error_spec: 
                   |${graphQLErrorMessageAttempt.orElseThrow()} ]""".flatten()
            )
        }
    }

    private fun updateRuntimeWiringBuilderInBuildContext(
        buildContext: GraphQLSchemaBuildContext
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """update_runtime_wiring_builder_in_build_context: 
                |[ scalars.size: ${buildContext.scalarTypeDefinitions.size}, 
                |wiring_factory.type: ${materializationGraphQLWiringFactory::class.qualifiedName} 
                |]""".flatten()
        )
        return buildContext.copy(
            runtimeWiringBuilder =
                buildContext.runtimeWiringBuilder.wiringFactory(materializationGraphQLWiringFactory)
        )
    }
}
