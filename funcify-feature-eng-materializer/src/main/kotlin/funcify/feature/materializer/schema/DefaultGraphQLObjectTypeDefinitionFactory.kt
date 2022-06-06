package funcify.feature.materializer.schema

import arrow.core.toOption
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.index.CompositeContainerType
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.flatMapOptions
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.Node
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLFieldsContainer
import org.slf4j.Logger

internal class DefaultGraphQLObjectTypeDefinitionFactory : GraphQLObjectTypeDefinitionFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLObjectTypeDefinitionFactory>()
    }

    override fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition {
        logger.debug(
            """create_object_type_definition_for_composite_container_type: 
                |[ composite_container_type.name: ${compositeContainerType.conventionalName} 
                |]""".flattenIntoOneLine()
        )
        if (compositeContainerType.canBeSourcedFrom(RawDataSourceType.GRAPHQL_API)) {
            return deriveObjectTypeDefinitionFromFirstAvailableGraphQLApiSourceContainerType(
                compositeContainerType
            )
        } else if (compositeContainerType.canBeSourcedFrom(RawDataSourceType.REST_API)) {
            TODO("deriving object_type_definition from rest_api_data_source not yet implemented")
        } else {
            TODO(
                "deriving object_type_definition from other type of data_source not yet implemented"
            )
        }
    }

    private fun deriveObjectTypeDefinitionFromFirstAvailableGraphQLApiSourceContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition {
        val firstGraphQLApiSourceContainerType: SourceContainerType<*> =
            (compositeContainerType
                .getSourceContainerTypeByDataSource()
                .asSequence()
                .filter { entry -> entry.key.sourceType == RawDataSourceType.GRAPHQL_API }
                .map { entry -> entry.value }
                .firstOrNull()
                ?: throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_api_source_container_type reported available but not found"
                ))
        val graphQLFieldsContainerType: GraphQLFieldsContainer =
            when (val graphQLSourceContainerType: SourceContainerType<*> =
                    firstGraphQLApiSourceContainerType
            ) {
                is GraphQLSourceContainerType -> {
                    graphQLSourceContainerType.containerType
                }
                else -> {
                    throw MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """graphql_source_container_type.type [ 
                                |expected: ${GraphQLSourceContainerType::class.qualifiedName}, 
                                |actual: ${firstGraphQLApiSourceContainerType::class.qualifiedName} 
                                |]""".flattenIntoOneLine()
                    )
                }
            }
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
                        .fieldDefinitions(
                            graphQLFieldsContainerType
                                .fieldDefinitions
                                .stream()
                                .map { fd -> fd.definition.toOption() }
                                .flatMapOptions()
                                .reduceToPersistentList()
                        )
                        .build()
                } else {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        .fieldDefinitions(
                            graphQLFieldsContainerType
                                .fieldDefinitions
                                .stream()
                                .map { fd -> fd.definition.toOption() }
                                .flatMapOptions()
                                .reduceToPersistentList()
                        )
                        .build()
                }
            } //
            // only object_type_definition if fieldsContainerType is actually a GraphQLObjectType
            is ObjectTypeDefinition -> {
                fieldsContainerDefinition
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    """graphql_fields_container_type does not conform to expected 
                                |contract for get_definition: [ graphql_field_container_type.definition.type: 
                                |${graphQLFieldsContainerType.definition::class.qualifiedName} 
                                |]""".flattenIntoOneLine()
                )
            }
        }
    }
}
