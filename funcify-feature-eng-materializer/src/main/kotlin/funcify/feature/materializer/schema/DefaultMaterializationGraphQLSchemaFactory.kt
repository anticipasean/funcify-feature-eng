package funcify.feature.materializer.schema

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.materializer.error.MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.sdl.GraphQLObjectTypeDefinitionFactory
import funcify.feature.materializer.sdl.GraphQLSDLFieldDefinitionFactory
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.JunctionVertex
import funcify.feature.schema.vertex.LeafVertex
import funcify.feature.schema.vertex.RootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
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
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLSchemaFactory(
    val objectMapper: ObjectMapper,
    val graphQLObjectTypeDefinitionFactory: GraphQLObjectTypeDefinitionFactory,
    val graphQLSDLFieldDefinitionFactory: GraphQLSDLFieldDefinitionFactory
) : MaterializationGraphQLSchemaFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLSchemaFactory>()

        private data class GraphQLSchemaBuildContext(
            val objectTypeDefinitionsByPath: PersistentMap<SchematicPath, ObjectTypeDefinition> =
                persistentMapOf(),
            val objectTypeDefinitionsByName: PersistentMap<String, ObjectTypeDefinition> =
                persistentMapOf(),
            val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            val sdlTypesByName: PersistentMap<String, Type<*>> = persistentMapOf(),
            val operationTypeDefinitionsByPath:
                PersistentMap<SchematicPath, OperationTypeDefinition> =
                persistentMapOf(),
            val runtimeWiringBuilder: RuntimeWiring.Builder = RuntimeWiring.newRuntimeWiring(),
            val scalarTypesByName: PersistentMap<String, GraphQLScalarType> = persistentMapOf(),
            val typeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry()
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
        metamodelGraph.verticesByPath.streamPairs().forEach {
            pair: Pair<SchematicPath, SchematicVertex> ->
            logger.info("[${++counter}]: [ path: ${pair.first}, vertex: [ ${pair.second}]")
        }
        return metamodelGraph
            .vertices
            .stream()
            .sorted { sv1, sv2 -> sv1.path.compareTo(sv2.path) }
            .reduce(
                Try.success(createInitialContextWithExtendedScalars()),
                { gqlbcTry: Try<GraphQLSchemaBuildContext>, sv: SchematicVertex ->
                    gqlbcTry.map { ctx: GraphQLSchemaBuildContext ->
                        addSchematicVertexToBuildContext(ctx, sv)
                    }
                },
                { bc1, _ -> bc1 }
            )
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

    private fun createInitialContextWithExtendedScalars(): GraphQLSchemaBuildContext {
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
        val extendedScalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
            extendedScalarTypes.fold(persistentMapOf<String, ScalarTypeDefinition>()) {
                pm,
                gqlScalarType ->
                val description: Description =
                    Description(
                        gqlScalarType.description,
                        SourceLocation.EMPTY,
                        gqlScalarType.description.contains('\n')
                    )
                pm.put(
                    gqlScalarType.name,
                    ScalarTypeDefinition.newScalarTypeDefinition()
                        .name(gqlScalarType.name)
                        .description(description)
                        .build()
                )
            }
        return GraphQLSchemaBuildContext(
            scalarTypeDefinitionsByName = extendedScalarTypeDefinitionsByName,
            scalarTypesByName =
                extendedScalarTypes
                    .parallelStream()
                    .map { gqls: GraphQLScalarType -> gqls.name to gqls }
                    .reducePairsToPersistentMap()
        )
    }

    private fun addSchematicVertexToBuildContext(
        buildContext: GraphQLSchemaBuildContext,
        vertex: SchematicVertex
    ): GraphQLSchemaBuildContext {
        logger.debug("add_schematic_vertex_to_build_context: [ vertex.path: ${vertex.path} ]")
        return when (vertex) {
            is RootVertex -> addRootVertexToBuildContext(buildContext, vertex)
            is JunctionVertex -> addJunctionVertexToBuildContext(buildContext, vertex)
            is LeafVertex -> addLeafVertexToBuildContext(buildContext, vertex)
            else ->
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    "unsupported schematic_vertex type: [ type: ${vertex::class.qualifiedName}]"
                )
        }
    }

    private fun addRootVertexToBuildContext(
        buildContext: GraphQLSchemaBuildContext,
        vertex: RootVertex
    ): GraphQLSchemaBuildContext {
        logger.debug("add_root_vertex_to_build_context: [ vertex.path: ${vertex.path} ]")
        val conventionalName: ConventionalName = vertex.compositeContainerType.conventionalName
        val queryObjectTypeName: TypeName = TypeName("Query")
        val queryOperationTypeDefinition: OperationTypeDefinition =
            OperationTypeDefinition.newOperationTypeDefinition()
                .name(conventionalName.toString())
                .typeName(queryObjectTypeName)
                .comments(listOf(Comment("root_type: ${vertex.path}", SourceLocation.EMPTY)))
                .build()
        val queryObjectTypeDefinition: ObjectTypeDefinition =
            ObjectTypeDefinition.newObjectTypeDefinition().name(queryObjectTypeName.name).build()
        return buildContext.copy(
            operationTypeDefinitionsByPath =
                buildContext.operationTypeDefinitionsByPath.put(
                    vertex.path,
                    queryOperationTypeDefinition
                ),
            objectTypeDefinitionsByPath =
                buildContext.objectTypeDefinitionsByPath.put(
                    vertex.path,
                    queryObjectTypeDefinition
                ),
            objectTypeDefinitionsByName =
                buildContext.objectTypeDefinitionsByName.put(
                    queryObjectTypeDefinition.name,
                    queryObjectTypeDefinition
                )
        )
    }

    private fun addJunctionVertexToBuildContext(
        buildContext: GraphQLSchemaBuildContext,
        vertex: JunctionVertex
    ): GraphQLSchemaBuildContext {
        logger.debug("add_junction_vertex_to_build_context: [ vertex.path: ${vertex.path} ]")
        val compositeContainerType = vertex.compositeContainerType
        val compositeAttribute = vertex.compositeAttribute
        val conventionalName: ConventionalName =
            if (compositeContainerType.conventionalName == compositeAttribute.conventionalName) {
                compositeContainerType.conventionalName
            } else {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """conventional_name instances on junction_vertex [ 
                        |container_type.name: ${compositeContainerType.conventionalName}, 
                        |attribute.name: ${compositeAttribute.conventionalName} ] do not match
                        |""".flattenIntoOneLine()
                )
            }
        val objectTypeDefinition: ObjectTypeDefinition =
            graphQLObjectTypeDefinitionFactory.createObjectTypeDefinitionForCompositeContainerType(
                compositeContainerType = compositeContainerType
            )
        val fieldDefinition: FieldDefinition =
            graphQLSDLFieldDefinitionFactory.createFieldDefinitionForCompositeAttribute(
                compositeAttribute = compositeAttribute
            )
        val parentPath: SchematicPath =
            vertex.path.getParentPath().getOrElse {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """root_path cannot be used as path for 
                       |junction_vertex [ path: ${vertex.path}, 
                       |name: $conventionalName ]
                       |""".flattenIntoOneLine()
                )
            }
        val parentObjectTypeDefinition: ObjectTypeDefinition =
            buildContext.objectTypeDefinitionsByPath[parentPath].toOption().getOrElse {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """object_type_definition for parent should be processed 
                       |before its children [ parent_path: ${parentPath}, 
                       |name: $conventionalName ]
                       |""".flattenIntoOneLine()
                )
            }
        val updatedParentDefinition =
            parentObjectTypeDefinition.transform { bldr: ObjectTypeDefinition.Builder ->
                bldr.fieldDefinition(fieldDefinition)
            }
        return if (objectTypeDefinition.name in buildContext.objectTypeDefinitionsByName) {
            buildContext.copy(
                objectTypeDefinitionsByPath =
                    buildContext
                        .objectTypeDefinitionsByPath
                        .put(parentPath, updatedParentDefinition)
                        .put(vertex.path, objectTypeDefinition),
                objectTypeDefinitionsByName =
                    buildContext.objectTypeDefinitionsByName.put(
                        updatedParentDefinition.name,
                        updatedParentDefinition
                    )
            )
        } else {
            buildContext.copy(
                objectTypeDefinitionsByPath =
                    buildContext
                        .objectTypeDefinitionsByPath
                        .put(parentPath, updatedParentDefinition)
                        .put(vertex.path, objectTypeDefinition),
                objectTypeDefinitionsByName =
                    buildContext
                        .objectTypeDefinitionsByName
                        .put(updatedParentDefinition.name, updatedParentDefinition)
                        .put(objectTypeDefinition.name, objectTypeDefinition)
            )
        }
    }

    private fun addLeafVertexToBuildContext(
        buildContext: GraphQLSchemaBuildContext,
        vertex: LeafVertex
    ): GraphQLSchemaBuildContext {
        logger.debug("add_leaf_vertex_to_build_context: [ vertex.path: ${vertex.path} ]")
        val compositeAttribute: CompositeAttribute = vertex.compositeAttribute
        val conventionalName: ConventionalName = compositeAttribute.conventionalName
        val fieldDefinition: FieldDefinition =
            graphQLSDLFieldDefinitionFactory.createFieldDefinitionForCompositeAttribute(
                compositeAttribute = compositeAttribute
            )
        val parentPath: SchematicPath =
            vertex.path.getParentPath().getOrElse {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """root_path cannot be used as path for 
                       |leaf_vertex [ path: ${vertex.path}, 
                       |name: $conventionalName ]
                       |""".flattenIntoOneLine()
                )
            }
        val parentObjectTypeDefinition: ObjectTypeDefinition =
            buildContext.objectTypeDefinitionsByPath[parentPath].toOption().getOrElse {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """object_type_definition for parent should be processed 
                       |before its children [ parent_path: ${parentPath}, 
                       |name: $conventionalName ]
                       |""".flattenIntoOneLine()
                )
            }
        val updatedParentDefinition =
            parentObjectTypeDefinition.transform { bldr: ObjectTypeDefinition.Builder ->
                bldr.fieldDefinition(fieldDefinition)
            }
        return buildContext.copy(
            objectTypeDefinitionsByPath =
                buildContext.objectTypeDefinitionsByPath.put(parentPath, updatedParentDefinition),
            objectTypeDefinitionsByName =
                buildContext.objectTypeDefinitionsByName.put(
                    updatedParentDefinition.name,
                    updatedParentDefinition
                )
        )
    }

    private fun createTypeDefinitionRegistryInBuildContext(
        buildContext: GraphQLSchemaBuildContext
    ): GraphQLSchemaBuildContext {
        logger.debug(
            """create_type_definition_registry_in_build_context: 
                |[ build_context.object_type_definitions_by_path.size: 
                |${buildContext.objectTypeDefinitionsByPath.size} 
                |]""".flattenIntoOneLine()
        )
        val currentTypeDefinitionRegistry: TypeDefinitionRegistry =
            buildContext.typeDefinitionRegistry
        currentTypeDefinitionRegistry
            .add(
                SchemaDefinition.newSchemaDefinition()
                    .operationTypeDefinitions(
                        buildContext.operationTypeDefinitionsByPath.values.toList()
                    )
                    .build()
            )
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("schema_definition"))
        currentTypeDefinitionRegistry
            .addAll(buildContext.scalarTypeDefinitionsByName.values)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("scalar_type_definitions"))
        currentTypeDefinitionRegistry
            .addAll(buildContext.objectTypeDefinitionsByName.values)
            .ifPresent(typeRegistryUpdateGraphQLErrorHandler("object_type_definitions"))
        return buildContext.copy(typeDefinitionRegistry = currentTypeDefinitionRegistry)
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
