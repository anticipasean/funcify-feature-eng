package funcify.feature.datasource.json

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import graphql.language.*

object GraphQLValueToJsonNodeConverter : (Value<*>) -> Option<JsonNode> {

    override fun invoke(graphQLValueNode: Value<*>): Option<JsonNode> {
        return when (graphQLValueNode) {
            is NullValue -> {
                JsonNodeFactory.instance.nullNode().some()
            }
            is StringValue -> {
                JsonNodeFactory.instance.textNode(graphQLValueNode.value).some()
            }
            is FloatValue -> {
                JsonNodeFactory.instance.numberNode(graphQLValueNode.value).some()
            }
            is IntValue -> {
                JsonNodeFactory.instance.numberNode(graphQLValueNode.value).some()
            }
            is BooleanValue -> {
                JsonNodeFactory.instance.booleanNode(graphQLValueNode.isValue).some()
            }
            is EnumValue -> {
                JsonNodeFactory.instance.textNode(graphQLValueNode.name).some()
            }
            is ArrayValue -> {
                graphQLValueNode.values
                    .asSequence()
                    .map { v: Value<*> -> invoke(v) }
                    .flatMapOptions()
                    .fold(JsonNodeFactory.instance.arrayNode()) { an, jn -> an.add(jn) }
                    .some()
            }
            is ObjectValue -> {
                graphQLValueNode.objectFields
                    .asSequence()
                    .map { of: ObjectField -> invoke(of.value).map { jn -> of.name to jn } }
                    .flatMapOptions()
                    .fold(JsonNodeFactory.instance.objectNode()) { on, (k, v) -> on.set(k, v) }
                    .some()
            }
            else -> {
                none()
            }
        }
    }
}
