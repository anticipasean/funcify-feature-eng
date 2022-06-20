package funcify.feature.datasource.graphql.sdl

import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.sdl.SourceContainerTypeGqlSdlObjectTypeDefinitionMapper
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.Node
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLFieldsContainer
import org.slf4j.Logger

class GraphQLSourceContainerTypeSDLDefinitionMapper :
    SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<GraphQLSourceContainerType> {

    companion object {
        private val logger: Logger = loggerFor<GraphQLSourceContainerTypeSDLDefinitionMapper>()
    }

    override fun convertSourceContainerTypeIntoGraphQLObjectTypeSDLDefinition(
        sourceContainerType: GraphQLSourceContainerType
    ): Try<ObjectTypeDefinition> {
        logger.debug(
            """convert_source_container_type_into_graphql_object_type_sdl_definition: 
            |[ source_container_type.name: ${sourceContainerType.containerType.name} ]
            |""".flattenIntoOneLine()
        )
        return Try.attempt {
            deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(sourceContainerType)
        }
    }

    private fun deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): ObjectTypeDefinition {
        val graphQLFieldsContainerType: GraphQLFieldsContainer =
            Try.attempt { graphQLSourceContainerType.containerType }.orElseThrow()
        return when (val fieldsContainerDefinition: Node<Node<*>>? =
                graphQLFieldsContainerType.definition
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
}
