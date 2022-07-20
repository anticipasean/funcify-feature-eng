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
    fun JsonNode.addChildFieldAndValuePairToRightmostTreeNode(
        childFieldNameValuePair: Pair<String, JsonNode>
    ): Option<JsonNode> {
        return this.addChildFieldAndValuePairToRightmostTreeNode(
            childFieldNameValuePair.first,
            childFieldNameValuePair.second
        )
    }

    /**
     * Finds the rightmost child node within the json node's tree and if it is an object type or
     * represents null or missing, the child field and value are set and returned
     * @return [Some(current_node with leftmost child_node having the field and value added)] else
     * [none]
     */
    fun JsonNode.addChildFieldAndValuePairToRightmostTreeNode(
        childFieldName: String,
        childJsonValue: JsonNode
    ): Option<JsonNode> {
        val root: String = "/"
        val fieldNameValueLineageList: ImmutableList<Pair<String, JsonNode>> =
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
        return sequenceOf(childFieldName to childJsonValue)
            .plus(fieldNameValueLineageList.asReversed().asSequence())
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
}
