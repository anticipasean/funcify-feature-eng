package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.getOrElse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterDirectiveArgumentAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataType: GraphQLInputType,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val appliedDirectiveArgument: Option<GraphQLAppliedDirectiveArgument>
) : GraphQLParameterAttribute {

    init {
        if (sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must represent an argument parameter on a 
                   |source_index i.e. have at least one argument 
                   |declared: [ actual: ${sourcePath} ]
                   |""".flattenIntoOneLine()
            )
        }
        if (sourcePath.directives.isNotEmpty() &&
                sourcePath.directives.asSequence().none { (_, jsonValue) ->
                    appliedDirectiveArgument
                        .map { gqlDirArg -> jsonValue.has(gqlDirArg.name) }
                        .getOrElse { false }
                }
        ) {
            val expectedArgName =
                appliedDirectiveArgument.map { gqlDirArg -> gqlDirArg.name }.getOrElse { "<NA>" }
            val objectNodeToStringTransformer: (ObjectNode) -> String = { on: ObjectNode ->
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(on)
            }
            val actualDirectivesAsJson: ObjectNode =
                sourcePath.directives.asSequence().fold(JsonNodeFactory.instance.objectNode()) {
                    on: ObjectNode,
                    (name: String, jsonValue: JsonNode) ->
                    on.set(name, jsonValue)
                }
            val actualDirectivesJsonStr: String =
                objectNodeToStringTransformer.invoke(actualDirectivesAsJson)
            val expectedDirectiveArgumentJsonExample: ObjectNode =
                JsonNodeFactory.instance.objectNode().putNull(expectedArgName)
            val expectedDirectiveArgumentJsonStr: String =
                objectNodeToStringTransformer.invoke(expectedDirectiveArgumentJsonExample)
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path for parameter_directive_argument_attribute 
                   |must have at least one directive 
                   |and have a key matching the name of 
                   |the applied_directive_argument of the 
                   |input: [ expected: 
                   |${expectedDirectiveArgumentJsonStr}, 
                   |actual: ${actualDirectivesJsonStr} ]
                   |""".flattenIntoOneLine()
            )
        }
    }
}
