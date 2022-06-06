package funcify.feature.materializer.schema

import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import org.slf4j.Logger

internal class DefaultGraphQLSDLFieldDefinitionFactory : GraphQLSDLFieldDefinitionFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLSDLFieldDefinitionFactory>()
    }

    override fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition {
        logger.debug(
            """create_field_definition_for_composite_attribute: [ 
                |composite_attribute.name: ${compositeAttribute.conventionalName} 
                |]""".flattenIntoOneLine()
        )
        if (compositeAttribute.canBeSourcedFrom(RawDataSourceType.GRAPHQL_API)) {
            return deriveFieldDefinitionFromGraphQLSourceAttribute(compositeAttribute)
        } else if (compositeAttribute.canBeSourcedFrom(RawDataSourceType.REST_API)) {
            TODO("deriving field_definition from rest_api_data_source not yet implemented")
        } else {
            TODO("deriving field_definition from other type of data_source not yet implemented")
        }
    }

    private fun deriveFieldDefinitionFromGraphQLSourceAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition {
        val firstAvailableGraphQLSourceAttribute: SourceAttribute =
            (compositeAttribute
                .getSourceAttributeByDataSource()
                .asSequence()
                .filter { entry -> entry.key.sourceType == RawDataSourceType.GRAPHQL_API }
                .map { entry -> entry.value }
                .firstOrNull()
                ?: throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_source_attribute reported available but not found"
                ))
        val graphQLFieldDefinitionOnThatSource: GraphQLFieldDefinition =
            when (firstAvailableGraphQLSourceAttribute) {
                is GraphQLSourceAttribute ->
                    firstAvailableGraphQLSourceAttribute.schemaFieldDefinition
                else ->
                    throw MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """graphql_source_attribute.type [ expected: 
                            |${GraphQLSourceAttribute::class.qualifiedName}, actual: 
                            |${firstAvailableGraphQLSourceAttribute::class.qualifiedName} 
                            |]""".flattenIntoOneLine()
                    )
            }
        return when (val sdlDefinition: FieldDefinition? =
                graphQLFieldDefinitionOnThatSource.definition
        ) {
            null -> {
                if (graphQLFieldDefinitionOnThatSource.description?.isNotEmpty() == true) {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLFieldDefinitionOnThatSource.name)
                        .description(
                            Description(
                                graphQLFieldDefinitionOnThatSource.description,
                                SourceLocation.EMPTY,
                                graphQLFieldDefinitionOnThatSource.description.contains('\n')
                            )
                        )
                        .type(
                            deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
                                graphQLOutputType = graphQLFieldDefinitionOnThatSource.type
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLFieldDefinitionOnThatSource.name)
                        .type(
                            deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
                                graphQLOutputType = graphQLFieldDefinitionOnThatSource.type
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

    fun deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
        graphQLOutputType: GraphQLOutputType
    ): Type<*> {
        return when (graphQLOutputType) {
            is GraphQLNonNull -> {
                if (graphQLOutputType.wrappedType is GraphQLList) {
                    if ((graphQLOutputType.wrappedType as GraphQLList).wrappedType is GraphQLNonNull
                    ) {
                        NonNullType.newNonNullType(
                                ListType.newListType(
                                        NonNullType.newNonNullType(
                                                TypeName.newTypeName(
                                                        (((graphQLOutputType.wrappedType as?
                                                                        GraphQLList)
                                                                    ?.wrappedType as?
                                                                    GraphQLNonNull)
                                                                ?.wrappedType as?
                                                                GraphQLNamedOutputType)
                                                            ?.name
                                                            ?: throw MaterializerException(
                                                                MaterializerErrorResponse
                                                                    .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                                "graphql_field_definition does not have name for use in SDL type creation"
                                                            )
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    } else {
                        NonNullType.newNonNullType(
                                ListType.newListType(
                                        TypeName.newTypeName(
                                                ((graphQLOutputType.wrappedType as? GraphQLList)
                                                        ?.wrappedType as?
                                                        GraphQLNamedOutputType)
                                                    ?.name
                                                    ?: throw MaterializerException(
                                                        MaterializerErrorResponse
                                                            .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                        "graphql_field_definition does not have name for use in SDL type creation"
                                                    )
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    }
                } else {
                    NonNullType.newNonNullType(
                            TypeName.newTypeName(
                                    (graphQLOutputType.wrappedType as? GraphQLNamedOutputType)?.name
                                        ?: throw MaterializerException(
                                            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                            "graphql_field_definition does not have name for use in SDL type creation"
                                        )
                                )
                                .build()
                        )
                        .build()
                }
            }
            is GraphQLList -> {
                if (graphQLOutputType.wrappedType is GraphQLNonNull) {
                    ListType.newListType(
                            NonNullType.newNonNullType(
                                    TypeName.newTypeName(
                                            ((graphQLOutputType.wrappedType as? GraphQLNonNull)
                                                    ?.wrappedType as?
                                                    GraphQLNamedOutputType)
                                                ?.name
                                                ?: throw MaterializerException(
                                                    MaterializerErrorResponse
                                                        .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                    "graphql_field_definition does not have name for use in SDL type creation"
                                                )
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                } else {
                    ListType.newListType(
                            TypeName.newTypeName(
                                    (graphQLOutputType.wrappedType as? GraphQLNamedOutputType)?.name
                                        ?: throw MaterializerException(
                                            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                            "graphql_field_definition does not have name for use in SDL type creation"
                                        )
                                )
                                .build()
                        )
                        .build()
                }
            }
            is GraphQLNamedOutputType -> {
                TypeName.newTypeName(
                        graphQLOutputType.name
                            ?: throw MaterializerException(
                                MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                "graphql_field_definition does not have name for use in SDL type creation"
                            )
                    )
                    .build()
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_field_definition does not have name for use in SDL type creation"
                )
            }
        }
    }
}
