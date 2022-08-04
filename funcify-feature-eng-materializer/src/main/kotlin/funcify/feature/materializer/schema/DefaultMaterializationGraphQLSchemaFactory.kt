package funcify.feature.materializer.schema

import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.impl.CompositeSDLDefinitionImplementationStrategy
import funcify.feature.materializer.error.MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.service.MaterializationGraphQLWiringFactory
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.GraphQLError
import graphql.language.DirectiveDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.OperationTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.SchemaDefinition
import graphql.language.TypeName
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLSchemaFactory(
    private val objectMapper: ObjectMapper,
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
                |] ]""".flattenIntoOneLine()
        )
        var counter: Int = 0
        if (logger.isDebugEnabled) {
            metamodelGraph.pathBasedGraph.verticesByPath.streamPairs().forEach {
                pair: Pair<SchematicPath, SchematicVertex> ->
                val vertexName =
                    when (val vertex = pair.second) {
                        is SourceRootVertex ->
                            vertex.compositeContainerType.conventionalName.toString()
                        is SourceJunctionVertex ->
                            vertex.compositeAttribute.conventionalName.toString()
                        is SourceLeafVertex -> vertex.compositeAttribute.conventionalName.toString()
                        is ParameterJunctionVertex ->
                            vertex.compositeParameterAttribute.conventionalName.toString()
                        is ParameterLeafVertex ->
                            vertex.compositeParameterAttribute.conventionalName.toString()
                        else -> "<NA>"
                    }
                logger.debug(
                    "[${++counter}]: [ path: ${pair.first}, vertex: [ type: ${pair.second::class.simpleName}, name: ${vertexName} ]"
                )
            }
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
                                |""".flattenIntoOneLine(),
                            t
                        )
                    }
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
               |""".flattenIntoOneLine()
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
                |]""".flattenIntoOneLine()
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
                       |cannot create operation definition""".flattenIntoOneLine()
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

    private fun typeRegistryUpdateGraphQLErrorHandler(definitionTypeBeingAdded: String) =
        { gqlError: GraphQLError ->
            val graphQLErrorMessage: String =
                objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(
                        objectMapper.valueToTree<JsonNode>(gqlError.toSpecification())
                    )
            throw MaterializerException(
                GRAPHQL_SCHEMA_CREATION_ERROR,
                """error reported when add $definitionTypeBeingAdded 
               |to type_definition_registry: [ error_spec: 
               |$graphQLErrorMessage ]""".flattenIntoOneLine()
            )
        }

    private fun updateRuntimeWiringBuilderInBuildContext(
        buildContext: GraphQLSchemaBuildContext
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """update_runtime_wiring_builder_in_build_context: 
                |[ scalars.size: 
                |${buildContext.scalarTypeDefinitions.size} ]
                |""".flattenIntoOneLine()
        )
        val runtimeWiringBuilder: RuntimeWiring.Builder =
            scalarTypeRegistry.getAllGraphQLScalarTypes().fold(
                buildContext.runtimeWiringBuilder.wiringFactory(materializationGraphQLWiringFactory)
            ) { rwBuilder: RuntimeWiring.Builder, scalarType: GraphQLScalarType ->
                rwBuilder.scalar(scalarType)
            }
        return buildContext.copy(runtimeWiringBuilder = runtimeWiringBuilder)
    }
}
