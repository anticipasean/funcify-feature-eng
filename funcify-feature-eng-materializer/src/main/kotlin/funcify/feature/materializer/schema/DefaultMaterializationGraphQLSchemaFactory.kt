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
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.GraphQLError
import graphql.language.*
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider

internal class DefaultMaterializationGraphQLSchemaFactory(
    val objectMapper: ObjectMapper,
    val sdlDefinitionCreationContextFactory: SchematicVertexSDLDefinitionCreationContextFactory,
    val sdlDefinitionImplementationStrategyProvider:
        ObjectProvider<SchematicVertexSDLDefinitionImplementationStrategy>
) : MaterializationGraphQLSchemaFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLSchemaFactory>()

        private data class ParentChildNodeCombinationContext(
            val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            val fieldDefinitionsBySchematicPath: PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            val argumentDefinitionBySchematicPath: PersistentMap<SchematicPath, Argument> =
                persistentMapOf(),
            val directiveDefinitionBySchematicPath:
                PersistentMap<SchematicPath, DirectiveDefinition> =
                persistentMapOf(),
            val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
                persistentMapOf()
        )

        private data class GraphQLSchemaBuildContext(
            val metamodelGraph: MetamodelGraph,
            val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            val scalarTypeDefinitions: PersistentList<ScalarTypeDefinition> = persistentListOf(),
            val implementingTypeDefinitions: PersistentList<ImplementingTypeDefinition<*>> =
                persistentListOf(),
            val runtimeWiringBuilder: RuntimeWiring.Builder = RuntimeWiring.newRuntimeWiring(),
            val typeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry()
        )
    }

    private val compositeSDLDefinitionImplementationStrategy:
        CompositeSDLDefinitionImplementationStrategy by lazy {
        CompositeSDLDefinitionImplementationStrategy(
            sdlDefinitionImplementationStrategies =
                sdlDefinitionImplementationStrategyProvider.toList()
        )
    }

    override fun createGraphQLSchemaFromMetamodelGraph(
        metamodelGraph: MetamodelGraph
    ): Try<GraphQLSchema> {
        val firstVertexPathSupplier: () -> SchematicPath? = { ->
            metamodelGraph
                .vertices
                .toOption()
                .filter { v -> v.size > 0 }
                .map { v -> v[0].path }
                .orNull()
        }
        logger.info(
            """create_graphql_schema_from_metamodel_graph: [ 
                |metamodel_graph: [ vertices.size: ${metamodelGraph.vertices.size}, 
                |vertices[0].path: ${firstVertexPathSupplier.invoke()} 
                |] ]""".flattenIntoOneLine()
        )
        var counter: Int = 0
        if (logger.isDebugEnabled) {
            metamodelGraph.verticesByPath.streamPairs().forEach {
                pair: Pair<SchematicPath, SchematicVertex> ->
                logger.debug("[${++counter}]: [ path: ${pair.first}, vertex: [ ${pair.second}]")
            }
        }
        return metamodelGraph
            .vertices
            .stream()
            .sorted { sv1, sv2 -> sv1.path.compareTo(sv2.path) }
            .reduce(
                Try.success(createInitialContextWithExtendedScalars(metamodelGraph)),
                { gqlbcTry: Try<SchematicVertexSDLDefinitionCreationContext<*>>, sv: SchematicVertex
                    ->
                    gqlbcTry
                        .flatMap { ctx: SchematicVertexSDLDefinitionCreationContext<*> ->
                            if (compositeSDLDefinitionImplementationStrategy.canBeAppliedToContext(
                                    ctx
                                )
                            ) {
                                compositeSDLDefinitionImplementationStrategy.applyToContext(ctx)
                            } else {
                                Try.failure<SchematicVertexSDLDefinitionCreationContext<*>>(
                                    MaterializerException(
                                        GRAPHQL_SCHEMA_CREATION_ERROR,
                                        "no strategy could be applied to context: [ context.path: ${ctx.path} ]"
                                    )
                                )
                            }
                        }
                        .map { ctx -> ctx.update { nextVertex(sv) } }
                },
                { sdlDefCreationCtx, _ -> sdlDefCreationCtx }
            )
            .map { ctx: SchematicVertexSDLDefinitionCreationContext<*> ->
                createBuildContextPlacingDependentDefinitionsInImplementingTypeDefinitions(ctx)
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

    private fun createInitialContextWithExtendedScalars(
        metamodelGraph: MetamodelGraph
    ): SchematicVertexSDLDefinitionCreationContext<*> {
        val extendedScalarTypes: PersistentList<GraphQLScalarType> =
            persistentListOf(
                ExtendedScalars.GraphQLBigDecimal,
                ExtendedScalars.GraphQLBigInteger,
                ExtendedScalars.GraphQLByte,
                ExtendedScalars.GraphQLChar,
                ExtendedScalars.GraphQLLong,
                ExtendedScalars.GraphQLShort,
                ExtendedScalars.Date,
                ExtendedScalars.DateTime,
                ExtendedScalars.Time,
                ExtendedScalars.Json,
                ExtendedScalars.Locale,
                ExtendedScalars.PositiveFloat,
                ExtendedScalars.PositiveInt,
                ExtendedScalars.NegativeFloat,
                ExtendedScalars.NegativeInt,
                ExtendedScalars.Url
            )
        val scalarTypeDefinitions: ImmutableList<ScalarTypeDefinition> =
            extendedScalarTypes.fold(persistentListOf<ScalarTypeDefinition>()) {
                pl: PersistentList<ScalarTypeDefinition>,
                gqlScalarType: GraphQLScalarType ->
                val description: Description =
                    Description(
                        gqlScalarType.description,
                        SourceLocation.EMPTY,
                        gqlScalarType.description?.contains('\n') ?: false
                    )
                pl.add(
                    ScalarTypeDefinition.newScalarTypeDefinition()
                        .name(gqlScalarType.name)
                        .description(description)
                        .build()
                )
            }
        return sdlDefinitionCreationContextFactory
            .createInitialContextForRootSchematicVertexSDLDefinition(
                metamodelGraph = metamodelGraph,
                scalarTypeDefinitions = scalarTypeDefinitions
            )
    }

    private fun createBuildContextPlacingDependentDefinitionsInImplementingTypeDefinitions(
        sdlDefinitionCreationContext: SchematicVertexSDLDefinitionCreationContext<*>
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """create_build_context_placing_dependent_definitions_
               |in_implementing_type_definitions: [ sdl_definition_creation_context.
               |sdl_definitions_by_schematic_path.size: 
               |${sdlDefinitionCreationContext.sdlDefinitionsBySchematicPath.size} ]
               |""".flattenIntoOneLine()
        )
        val sdlDefinitionsBySchematicPath: ImmutableMap<SchematicPath, ImmutableSet<Node<*>>> =
            sdlDefinitionCreationContext.sdlDefinitionsBySchematicPath
        sdlDefinitionsBySchematicPath
            .streamPairs()
            .sorted { p1, p2 -> p1.first.compareTo(p2.first) }
            .flatMap { pathAndNodes ->
                pathAndNodes.second.stream().map { node -> pathAndNodes.first to node }
            }
            .reduce(
                ParentChildNodeCombinationContext(),
                { ctx, (path, node) ->
                    updateParentChildCombineContextWithNewRelationshipDefinition(ctx, path, node)
                },
                { ctx, _ -> ctx }
            )
    }

    private fun updateParentChildCombineContextWithNewRelationshipDefinition(
        parentChildNodeCombinationContext: ParentChildNodeCombinationContext,
        path: SchematicPath,
        node: Node<*>,
    ): ParentChildNodeCombinationContext {
        return when (node) {
            is ImplementingTypeDefinition<*> -> {
                parentChildNodeCombinationContext.copy(
                    implementingTypeDefinitionsBySchematicPath =
                        parentChildNodeCombinationContext.implementingTypeDefinitionsBySchematicPath
                            .put(path, node)
                )
            }
            is FieldDefinition -> {
                parentChildNodeCombinationContext.copy(
                    fieldDefinitionsBySchematicPath =
                        parentChildNodeCombinationContext.fieldDefinitionsBySchematicPath.put(
                            path,
                            node
                        )
                )
            }
            is Argument -> {
                parentChildNodeCombinationContext.copy(
                    argumentDefinitionBySchematicPath =
                        parentChildNodeCombinationContext.argumentDefinitionBySchematicPath.put(
                            path,
                            node
                        )
                )
            }
            is DirectiveDefinition -> {
                parentChildNodeCombinationContext.copy(
                    directiveDefinitionBySchematicPath =
                        parentChildNodeCombinationContext.directiveDefinitionBySchematicPath.put(
                            path,
                            node
                        )
                )
            }
            is InputObjectTypeDefinition -> {
                parentChildNodeCombinationContext.copy(
                    inputObjectTypeDefinitionsBySchematicPath =
                        parentChildNodeCombinationContext.inputObjectTypeDefinitionsBySchematicPath
                            .put(path, node)
                )
            }
            is InputValueDefinition -> {
                parentChildNodeCombinationContext.copy(
                    inputValueDefinitionsBySchematicPath =
                        parentChildNodeCombinationContext.inputValueDefinitionsBySchematicPath.put(
                            path,
                            node
                        )
                )
            }
            else -> {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    "unhandled graphql node type: [ actual: ${node::class.qualifiedName} ]"
                )
            }
        }
    }

    private fun createTypeDefinitionRegistryInBuildContext(
        buildContext: GraphQLSchemaBuildContext
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """create_type_definition_registry_in_build_context: 
                |[ build_context.sdl_definitions_by_schematic_path.size: 
                |${buildContext.sdlDefinitionsBySchematicPath.size} 
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
            .addAll(buildContext.implementingTypeDefinitions)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("implementing_type_definitions"))
        return buildContext.copy(typeDefinitionRegistry = typeDefinitionRegistry)
    }

    private fun createQueryOperationDefinitionFromSourceRootVertex(
        metamodelGraph: MetamodelGraph
    ): OperationTypeDefinition {
        return when (val sourceRootVertex: SourceRootVertex? =
                metamodelGraph.verticesByPath[SchematicPath.getRootPath()] as? SourceRootVertex
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
                        StandardNamingConventions.CAMEL_CASE
                            .deriveName(
                                sourceRootVertex.compositeContainerType.conventionalName.toString()
                            )
                            .toString()
                    )
                    .typeName(
                        TypeName(
                            StandardNamingConventions.PASCAL_CASE
                                .deriveName(
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
                |[ build_context.graphql_scalar_types.size: 
                |${buildContext.scalarTypesByName.size} ]
                |""".flattenIntoOneLine()
        )
        val runtimeWiringBuilder: RuntimeWiring.Builder =
            buildContext.scalarTypesByName.values.fold(buildContext.runtimeWiringBuilder) {
                rwBuilder: RuntimeWiring.Builder,
                scalarType: GraphQLScalarType ->
                rwBuilder.scalar(scalarType)
            }
        // TODO: Insert wiring factory here
        return buildContext.copy(runtimeWiringBuilder = runtimeWiringBuilder)
    }
}
