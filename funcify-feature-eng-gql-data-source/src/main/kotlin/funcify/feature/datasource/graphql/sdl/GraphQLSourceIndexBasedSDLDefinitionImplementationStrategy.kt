package funcify.feature.datasource.graphql.sdl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.impl.DataSourceIndexTypeBasedSDLDefinitionStrategy
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.Node
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLFieldsContainer
import kotlin.reflect.full.isSubclassOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-02
 */
class GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy :
    DataSourceIndexTypeBasedSDLDefinitionStrategy<
        GraphQLSourceIndex, SchematicVertexSDLDefinitionCreationContext<*>>(
        GraphQLSourceIndex::class
    ),
    SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger =
            loggerFor<GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy>()

        private object DefaultGraphQLParameterIndexSDLDefinitionCreationFactory :
            GraphQLParameterIndexSDLDefinitionCreationTemplate {}
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("apply_to_context: [ context.path: ${context.path} ]")
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                val compositeSourceContainerType: CompositeSourceContainerType =
                    context.compositeSourceContainerType
                createObjectTypeDefinitionForGraphQLSourceContainerType(
                        compositeSourceContainerType,
                        context
                    )
                    .map { objectTypeDef ->
                        context.update {
                            addSDLDefinitionForSchematicPath(context.path, objectTypeDef)
                        }
                    }
            }
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                val compositeSourceContainerType: CompositeSourceContainerType =
                    context.compositeSourceContainerType
                val compositeSourceAttribute: CompositeSourceAttribute =
                    context.compositeSourceAttribute
                createObjectTypeDefinitionForGraphQLSourceContainerType(
                        compositeSourceContainerType,
                        context
                    )
                    .zip(
                        createFieldDefinitionForGraphQLSourceAttributeType(
                            compositeSourceAttribute,
                            context
                        )
                    )
                    .map { (objectTypeDef, fieldDef) ->
                        context.update {
                            addSDLDefinitionForSchematicPath(context.path, objectTypeDef)
                                .addSDLDefinitionForSchematicPath(context.path, fieldDef)
                        }
                    }
            }
            is SourceLeafVertexSDLDefinitionCreationContext -> {
                val compositeSourceAttribute: CompositeSourceAttribute =
                    context.compositeSourceAttribute
                createFieldDefinitionForGraphQLSourceAttributeType(
                        compositeSourceAttribute,
                        context
                    )
                    .map { fieldDef ->
                        context.update { addSDLDefinitionForSchematicPath(context.path, fieldDef) }
                    }
            }
            is ParameterJunctionVertexSDLDefinitionCreationContext -> {
                DefaultGraphQLParameterIndexSDLDefinitionCreationFactory
                    .createParameterSDLDefinitionForParameterJunctionVertexInContext(context)
            }
            is ParameterLeafVertexSDLDefinitionCreationContext -> {
                DefaultGraphQLParameterIndexSDLDefinitionCreationFactory
                    .createParameterSDLDefinitionForParameterLeafVertexInContext(context)
            }
        }
    }

    private fun createObjectTypeDefinitionForGraphQLSourceContainerType(
        compositeSourceContainerType: CompositeSourceContainerType,
        context: SchematicVertexSDLDefinitionCreationContext<*>,
    ): Try<ObjectTypeDefinition> {
        val graphQLSourceContainerTypes: List<GraphQLSourceContainerType> =
            compositeSourceContainerType
                .getSourceContainerTypeByDataSource()
                .keys
                .asSequence()
                .filter { dsKey -> dsKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class) }
                .map { dsKey ->
                    compositeSourceContainerType.getSourceContainerTypeByDataSource()[dsKey]!!
                }
                .filterIsInstance<GraphQLSourceContainerType>()
                .toList()
        return when (graphQLSourceContainerTypes.size) {
            0 -> {
                val sourceContainerTypeDataSourceKeysSet =
                    compositeSourceContainerType
                        .getSourceContainerTypeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceContainerType>(
                    DataSourceException(
                        DataSourceErrorResponse.STRATEGY_INCORRECTLY_APPLIED,
                        """expected at least one graphql_data_source 
                           |for vertex in context [ vertex.path: ${context.path}, 
                           |vertex.composite_container_type
                           |.data_source_keys: $sourceContainerTypeDataSourceKeysSet 
                           |]""".flattenIntoOneLine()
                    )
                )
            }
            1 -> {
                Try.success(graphQLSourceContainerTypes.first())
            }
            else -> {
                val graphQLSourceContainerTypesFoundSet =
                    graphQLSourceContainerTypes
                        .map { gqlsct -> gqlsct.dataSourceLookupKey }
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceContainerType>(
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                        """more than one gql_data_source provides 
                           |values for container_type [ container_type.path: 
                           |${graphQLSourceContainerTypes.first().sourcePath}, 
                           |gql_data_sources: $graphQLSourceContainerTypesFoundSet ] 
                           |handling for multiple sources for same container_type 
                           |undefined""".flattenIntoOneLine()
                    )
                )
            }
        }.map { graphQLSourceContainerType: GraphQLSourceContainerType ->
            deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(graphQLSourceContainerType)
        }
    }

    private fun createFieldDefinitionForGraphQLSourceAttributeType(
        compositeSourceAttribute: CompositeSourceAttribute,
        context: SchematicVertexSDLDefinitionCreationContext<*>,
    ): Try<FieldDefinition> {
        val graphQLSourceAttributes: List<GraphQLSourceAttribute> =
            compositeSourceAttribute
                .getSourceAttributeByDataSource()
                .keys
                .asSequence()
                .filter { dsKey -> dsKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class) }
                .filterIsInstance<DataSource.Key<GraphQLSourceIndex>>()
                .map { dsKey ->
                    compositeSourceAttribute.getSourceAttributeForDataSourceKey<
                        GraphQLSourceIndex, GraphQLSourceAttribute>(dsKey)
                }
                .filterIsInstance<GraphQLSourceAttribute>()
                .toList()
        return when (graphQLSourceAttributes.size) {
            0 -> {
                val sourceAttributeDataSourceKetSet =
                    compositeSourceAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceAttribute>(
                    DataSourceException(
                        DataSourceErrorResponse.STRATEGY_INCORRECTLY_APPLIED,
                        """expected at least one gql_data_source 
                           |for vertex in context [ vertex.path: ${context.path}, 
                           |vertex.composite_attribute
                           |.data_source_keys: $sourceAttributeDataSourceKetSet 
                           |]""".flattenIntoOneLine()
                    )
                )
            }
            1 -> {
                Try.success(graphQLSourceAttributes.first())
            }
            else -> {
                val graphQLSourceContainerTypesFoundSet =
                    graphQLSourceAttributes
                        .map { gqlsct -> gqlsct.dataSourceLookupKey }
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceAttribute>(
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                        """more than one gql_data_source provides 
                           |values for attribute [ attribute.path: 
                           |${graphQLSourceAttributes.first().sourcePath}, 
                           |gql_data_sources: $graphQLSourceContainerTypesFoundSet ] 
                           |handling for multiple sources for same container_type 
                           |undefined""".flattenIntoOneLine()
                    )
                )
            }
        }.map { graphQLSourceAttribute: GraphQLSourceAttribute ->
            deriveFieldDefinitionFromGraphQLSourceAttribute(graphQLSourceAttribute)
        }
    }

    private fun deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): ObjectTypeDefinition {
        val graphQLFieldsContainerType: GraphQLFieldsContainer =
            Try.attempt { graphQLSourceContainerType.graphQLFieldsContainerType }.orElseThrow()
        return when (
            val fieldsContainerDefinition: Node<Node<*>>? = graphQLFieldsContainerType.definition
        ) { //
            // null if fieldsContainerType was not based on an SDL definition
            null -> {
                if (graphQLFieldsContainerType.description?.isNotEmpty() == true) {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        .description(
                            Description(
                                graphQLFieldsContainerType.description ?: "",
                                SourceLocation.EMPTY,
                                graphQLFieldsContainerType.description?.contains('\n') ?: false
                            )
                        )
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *    graphQLFieldsContainerType
                         *        .fieldDefinitions
                         *        .stream()
                         *        .map { fd -> fd.definition.toOption() }
                         *        .flatMapOptions()
                         *        .reduceToPersistentList()
                         *)
                         */
                        .build()
                } else {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *     graphQLFieldsContainerType
                         *         .fieldDefinitions
                         *         .stream()
                         *         .map { fd -> fd.definition.toOption() }
                         *         .flatMapOptions()
                         *         .reduceToPersistentList()
                         * )
                         */
                        .build()
                }
            } //
            // only object_type_definition if fieldsContainerType is actually a GraphQLObjectType
            is ObjectTypeDefinition -> {
                fieldsContainerDefinition.transform { builder: ObjectTypeDefinition.Builder ->
                    /*
                     * Clear list of field_definitions already available in given object_type_definition
                     * Let this list be updated as source_attributes are updated
                     */
                    builder.fieldDefinitions(listOf())
                }
            }
            else -> {
                throw GQLDataSourceException(
                    GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                    """graphql_fields_container_type does not conform to expected 
                      |contract for get_definition: [ graphql_field_container_type.definition.type: 
                      |${fieldsContainerDefinition::class.qualifiedName} 
                      |]""".flattenIntoOneLine()
                )
            }
        }
    }

    private fun deriveFieldDefinitionFromGraphQLSourceAttribute(
        graphQLSourceAttribute: GraphQLSourceAttribute
    ): FieldDefinition {
        return when (
            val sdlDefinition: FieldDefinition? =
                graphQLSourceAttribute.graphQLFieldDefinition.definition
        ) {
            null -> {
                if (
                    graphQLSourceAttribute.graphQLFieldDefinition.description?.isNotEmpty() == true
                ) {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.graphQLFieldDefinition.name)
                        .description(
                            Description(
                                graphQLSourceAttribute.graphQLFieldDefinition.description,
                                SourceLocation.EMPTY,
                                graphQLSourceAttribute.graphQLFieldDefinition.description?.contains(
                                    '\n'
                                )
                                    ?: false
                            )
                        )
                        .type(
                            GraphQLSDLTypeComposer.invoke(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.graphQLFieldDefinition.type
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.graphQLFieldDefinition.name)
                        .type(
                            GraphQLSDLTypeComposer.invoke(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.graphQLFieldDefinition.type
                            )
                        )
                        .build()
                }
            }
            else -> {
                sdlDefinition
            }
        }
    }
}
