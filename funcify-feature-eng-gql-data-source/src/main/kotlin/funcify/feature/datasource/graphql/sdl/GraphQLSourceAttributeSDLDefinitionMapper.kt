package funcify.feature.datasource.graphql.sdl

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.sdl.SourceAttributeGqlSdlFieldDefinitionMapper
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.InputValueDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger

class GraphQLSourceAttributeSDLDefinitionMapper :
    SourceAttributeGqlSdlFieldDefinitionMapper<GraphQLSourceAttribute> {

    companion object {
        private val logger: Logger = loggerFor<GraphQLSourceContainerTypeSDLDefinitionMapper>()
    }
    override fun convertSourceAttributeIntoGraphQLFieldSDLDefinition(
        sourceAttribute: GraphQLSourceAttribute
    ): Try<FieldDefinition> {
        logger.debug(
            """convert_source_attribute_into_graphql_field_sdl_definition: 
            |[ source_attribute.name: ${sourceAttribute.name} ]
            |""".flattenIntoOneLine()
        )
        return Try.attempt { deriveFieldDefinitionFromGraphQLSourceAttribute(sourceAttribute) }
    }

    private fun deriveFieldDefinitionFromGraphQLSourceAttribute(
        graphQLSourceAttribute: GraphQLSourceAttribute
    ): FieldDefinition {
        return when (val sdlDefinition: FieldDefinition? =
                graphQLSourceAttribute.schemaFieldDefinition.definition
        ) {
            null -> {
                if (graphQLSourceAttribute.schemaFieldDefinition.description?.isNotEmpty() == true
                ) {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.schemaFieldDefinition.name)
                        .description(
                            Description(
                                graphQLSourceAttribute.schemaFieldDefinition.description,
                                SourceLocation.EMPTY,
                                graphQLSourceAttribute.schemaFieldDefinition.description.contains(
                                    '\n'
                                )
                            )
                        )
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.schemaFieldDefinition.type
                            )
                        )
                        .inputValueDefinitions(
                            extractInputValueDefinitionsFromArgumentsOnFieldDefinitionSource(
                                graphQLSourceAttribute.sourcePath,
                                graphQLSourceAttribute.schemaFieldDefinition
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.schemaFieldDefinition.name)
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.schemaFieldDefinition.type
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

    private fun extractInputValueDefinitionsFromArgumentsOnFieldDefinitionSource(
        sourceAttributePath: SchematicPath,
        definition: GraphQLFieldDefinition
    ): List<InputValueDefinition> {
        return if (definition.arguments.isEmpty()) {
            emptyList()
        } else {
            definition
                .arguments
                .map { graphQLArgument: GraphQLArgument ->
                    InputValueDefinition.newInputValueDefinition()
                        .name(graphQLArgument.name)
                        .description(
                            Description(
                                sourceAttributePath
                                    .transform {
                                        argument(
                                            graphQLArgument.name,
                                            graphQLArgument.argumentDefaultValue.value?.toString()
                                                ?: ""
                                        )
                                    }
                                    .toString(),
                                SourceLocation.EMPTY,
                                false
                            )
                        )
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLArgument.type
                            )
                        )
                        .build()
                }
                .toPersistentList()
        }
    }

    private fun recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
        graphQLInputOrOutputType: GraphQLType
    ): Type<*> {
        if (graphQLInputOrOutputType !is GraphQLOutputType &&
                graphQLInputOrOutputType !is GraphQLInputType
        ) {
            val message =
                """the graphql_type passed in as input is 
                   |neither an input or output type 
                   |so an SDL Type<*> instance cannot be determined: 
                   |[ actual: ${graphQLInputOrOutputType::class.qualifiedName} 
                   |]""".flattenIntoOneLine()
            throw GQLDataSourceException(GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR, message)
        }
        val traversalFunction:
            (Pair<(Type<*>) -> Type<*>, GraphQLType>) -> Option<
                    Either<Pair<(Type<*>) -> Type<*>, GraphQLType>, Type<*>>> =
            { pair: Pair<(Type<*>) -> Type<*>, GraphQLType> ->
                when (val graphQLType: GraphQLType = pair.second) {
                    is GraphQLNonNull ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                NonNullType.newNonNullType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLList ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                ListType.newListType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLNamedType ->
                        pair.first
                            .invoke(TypeName.newTypeName(graphQLType.name).build())
                            .right()
                            .some()
                    else -> none()
                }
            }
        return TraversalFunctions.recurseWithOption(
                ({ t: Type<*> -> t } to graphQLInputOrOutputType),
                traversalFunction
            )
            .fold(
                { ->
                    throw unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
                        graphQLInputOrOutputType
                    )
                },
                { t: Type<*> -> t }
            )
    }

    private fun unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
        graphQLInputOrOutputType: GraphQLType
    ): GQLDataSourceException {
        val inputOrOutputType: String =
            if (graphQLInputOrOutputType is GraphQLInputType) {
                "input_type"
            } else {
                "output_type"
            }
        return GQLDataSourceException(
            GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
            """graphql_field_definition.${inputOrOutputType} [ type.to_string: 
                |$graphQLInputOrOutputType ] 
                |does not have name for use in SDL type creation
                |""".flattenIntoOneLine()
        )
    }
}
