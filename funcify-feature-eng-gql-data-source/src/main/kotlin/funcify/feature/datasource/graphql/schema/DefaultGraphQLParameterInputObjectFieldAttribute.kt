package funcify.feature.datasource.graphql.schema

import arrow.core.Option
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
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
internal data class DefaultGraphQLParameterInputObjectFieldAttribute(
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val dataType: GraphQLInputType,
    override val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
    override val inputObjectField: Option<GraphQLInputObjectField>
) : GraphQLParameterAttribute {

    init {
        if (sourcePath.arguments.isEmpty() && sourcePath.directives.isEmpty()) {
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path must represent a parameter on a 
                   |source_index i.e. have at least one argument or
                   |directive declared: [ actual: ${sourcePath} ]
                   |""".flattenIntoOneLine()
            )
        }
        if (sourcePath.arguments.isNotEmpty() &&
                sourcePath
                    .arguments
                    .asSequence()
                    .filter { (_, jsonValue) -> jsonValue.isObject }
                    .none { (_, jsonValue) ->
                        inputObjectField
                            .map { f -> f.name }
                            .filter { fname -> jsonValue.has(fname) }
                            .isDefined()
                    }
        ) {
            val objectNodeToStringTransformer: (ObjectNode) -> String = { on: ObjectNode ->
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(on)
            }
            val argumentsAsObjectNode: ObjectNode =
                sourcePath.arguments.asSequence().fold(JsonNodeFactory.instance.objectNode()) {
                    on: ObjectNode,
                    (name: String, jsonValue: JsonNode) ->
                    on.set(name, jsonValue)
                }
            val argsObjectNodeAsString: String =
                objectNodeToStringTransformer.invoke(argumentsAsObjectNode)

            val exampleInputObjectFieldEntryObjectNode: ObjectNode =
                JsonNodeFactory.instance
                    .objectNode()
                    .putNull(inputObjectField.map { f -> f.name }.orNull() ?: "<NA>")
            val exampleInputObjectFieldAsString: String =
                objectNodeToStringTransformer.invoke(exampleInputObjectFieldEntryObjectNode)
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path with arguments must have an 
                    |object type value with a key with the 
                    |same name as the input_object_field 
                    |specified for this parameter_attribute: 
                    |[ expected_node_in_example_form: $exampleInputObjectFieldAsString, 
                    |actual_arguments_as_json_node: $argsObjectNodeAsString ]""".flattenIntoOneLine()
            )
        }
        if (sourcePath.directives.isNotEmpty() &&
                sourcePath
                    .directives
                    .asSequence()
                    .filter { (_, jsonValue) -> jsonValue.isObject }
                    .none { (_, jsonValue) ->
                        inputObjectField
                            .map { f -> f.name }
                            .filter { fname -> jsonValue.has(fname) }
                            .isDefined()
                    }
        ) {
            val objectNodeToStringTransformer: (ObjectNode) -> String = { on: ObjectNode ->
                ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(on)
            }
            val directivesAsObjectNode: ObjectNode =
                sourcePath.directives.asSequence().fold(JsonNodeFactory.instance.objectNode()) {
                    on: ObjectNode,
                    (name: String, jsonValue: JsonNode) ->
                    on.set(name, jsonValue)
                }
            val dirsObjectNodeAsString: String =
                objectNodeToStringTransformer.invoke(directivesAsObjectNode)

            val exampleInputObjectFieldEntryObjectNode: ObjectNode =
                JsonNodeFactory.instance
                    .objectNode()
                    .putNull(inputObjectField.map { f -> f.name }.orNull() ?: "<NA>")
            val exampleInputObjectFieldAsString: String =
                objectNodeToStringTransformer.invoke(exampleInputObjectFieldEntryObjectNode)
            throw GQLDataSourceException(
                GQLDataSourceErrorResponse.INVALID_INPUT,
                """source_path with directives must have an 
                    |object type value with a key with the 
                    |same name as the input_object_field 
                    |specified for this parameter_attribute: 
                    |[ expected_node_in_example_form: $exampleInputObjectFieldAsString, 
                    |actual_directives_as_json_node: $dirsObjectNodeAsString ]""".flattenIntoOneLine()
            )
        }
    }
}
