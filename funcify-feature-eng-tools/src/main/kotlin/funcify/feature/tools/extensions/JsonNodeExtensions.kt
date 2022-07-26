package funcify.feature.tools.extensions

import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.extensions.PersistentListExtensions.toImmutableList
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableList

object JsonNodeExtensions {

    /**
     * Finds the rightmost child node within the json node's tree and if it is an object type or
     * represents null or missing, the child field and value are set and returned
     * @return [Some(current_node with leftmost child_node having the field and value added)] else
     * [none]
     */
    fun JsonNode.addChildKeyValuePairToRightmostObjectOrNullNode(
        childKeyValuePair: Pair<String, JsonNode>
    ): Option<JsonNode> {
        return this.addChildKeyValuePairToRightmostObjectOrNullNode(
            childKeyValuePair.first,
            childKeyValuePair.second
        )
    }

    /**
     * Finds the rightmost child node within the json node's tree and if it is an object type or
     * represents null or missing, the child field and value are set and returned
     * @return [Some(current_node with leftmost child_node having the field and value added)] else
     * [none]
     */
    fun JsonNode.addChildKeyValuePairToRightmostObjectOrNullNode(
        childKey: String,
        childJsonValue: JsonNode
    ): Option<JsonNode> {
        val root: String = "/"
        val keyValueLineageList: ImmutableList<Pair<String, JsonNode>> =
            TraversalFunctions.recurseWithStream(root to this) { (fName: String, jn: JsonNode) ->
                    when (jn.nodeType) {
                        JsonNodeType.OBJECT -> {
                            jn.fields()
                                .asSequence()
                                .lastOrNull()
                                .toOption()
                                .fold(
                                    { Stream.of((fName to jn).right()) },
                                    { (lastPropName, lastPropVal) ->
                                        Stream.of(
                                            (fName to jn).right(),
                                            (lastPropName to lastPropVal).left()
                                        )
                                    }
                                )
                        }
                        JsonNodeType.NULL,
                        JsonNodeType.MISSING -> {
                            Stream.of((fName to jn).right())
                        }
                        else -> {
                            Stream.empty()
                        }
                    }
                }
                .toImmutableList()
        return sequenceOf(childKey to childJsonValue)
            .plus(keyValueLineageList.asReversed())
            .reduceOrNull { childPair, parentPair ->
                parentPair.first to
                    (when (val parentValue = parentPair.second) {
                        is ObjectNode ->
                            parentValue.set<ObjectNode>(childPair.first, childPair.second)
                        is NullNode ->
                            JsonNodeFactory.instance
                                .objectNode()
                                .set(childPair.first, childPair.second)
                        is MissingNode ->
                            JsonNodeFactory.instance
                                .objectNode()
                                .set(childPair.first, childPair.second)
                        else -> parentValue
                    })
            }
            .toOption()
            .map { (_, jn) -> jn }
    }

    fun JsonNode.removeLastChildKeyValuePairFromRightmostObjectNode(): JsonNode {
        val root: String = "/"
        val keyValueLineageList: ImmutableList<Pair<String, JsonNode>> =
            TraversalFunctions.recurseWithStream(root to this) { (fName: String, jn: JsonNode) ->
                    when (jn.nodeType) {
                        JsonNodeType.OBJECT -> {
                            jn.fields()
                                .asSequence()
                                .lastOrNull()
                                .toOption()
                                .fold(
                                    { Stream.of((fName to jn).right()) },
                                    { (lastPropName, lastPropVal) ->
                                        Stream.of(
                                            (fName to jn).right(),
                                            (lastPropName to lastPropVal).left()
                                        )
                                    }
                                )
                        }
                        else -> {
                            Stream.empty()
                        }
                    }
                }
                .toImmutableList()
        return when {
            keyValueLineageList.size >= 1 -> {
                keyValueLineageList
                    .lastOrNull()
                    .toOption()
                    .map { lastParentKeyValue ->
                        lastParentKeyValue.first to
                            (when (val size = lastParentKeyValue.second.size()) {
                                0,
                                1 -> JsonNodeFactory.instance.objectNode()
                                else -> {
                                    lastParentKeyValue.second
                                        .fields()
                                        .asSequence()
                                        .take(size - 1)
                                        .fold(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                                            on.set(k, v)
                                        }
                                }
                            })
                    }
                    .mapNotNull { updatedParentKeyValue ->
                        sequenceOf(updatedParentKeyValue)
                            .plus(keyValueLineageList.asReversed().drop(1))
                            .reduceOrNull {
                                childPair: Pair<String, JsonNode>,
                                parentPair: Pair<String, JsonNode> ->
                                parentPair.first to
                                    (when (val parentJsonValue = parentPair.second) {
                                        is ObjectNode ->
                                            parentJsonValue.set<ObjectNode>(
                                                childPair.first,
                                                childPair.second
                                            )
                                        else -> parentJsonValue
                                    })
                            }
                            ?: updatedParentKeyValue
                    }
                    .map { (_, jn) -> jn }
                    .orNull()
                    ?: this
            }
            else -> this
        }
    }
}
