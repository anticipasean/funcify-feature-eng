package funcify.feature.materializer.schema

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.materializer.error.MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.JunctionVertex
import funcify.feature.schema.vertex.LeafVertex
import funcify.feature.schema.vertex.RootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Comment
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.OperationTypeDefinition
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeDefinition
import graphql.language.TypeName
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLSchemaFactory(
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
            val otherTypeDefinitionsByName: PersistentMap<String, TypeDefinition<*>> =
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
        metamodelGraph
            .vertices
            .stream()
            .sorted { sv1, sv2 -> sv1.path.compareTo(sv2.path) }
            .reduce(
                GraphQLSchemaBuildContext(),
                { gqlbc: GraphQLSchemaBuildContext, sv: SchematicVertex ->
                    addSchematicVertexToBuildContext(gqlbc, sv)
                },
                { bc1, _ -> bc1 }
            )
        return Try.failure(MaterializerException(GRAPHQL_SCHEMA_CREATION_ERROR))
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
                       |name: ${conventionalName} ]
                       |""".flattenIntoOneLine()
                )
            }
        val parentObjectTypeDefinition: ObjectTypeDefinition =
            buildContext.objectTypeDefinitionsByPath[parentPath].toOption().getOrElse {
                throw MaterializerException(
                    GRAPHQL_SCHEMA_CREATION_ERROR,
                    """object_type_definition for parent should be processed 
                       |before its children [ parent_path: ${parentPath}, 
                       |name: ${conventionalName} ]
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
        return buildContext
    }
}
