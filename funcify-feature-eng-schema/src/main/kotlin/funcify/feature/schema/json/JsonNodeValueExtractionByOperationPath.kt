package funcify.feature.schema.json

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.recurseBreadthFirst
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object JsonNodeValueExtractionByOperationPath : (JsonNode, GQLOperationPath) -> Option<JsonNode> {

    private val logger: Logger = loggerFor<JsonNodeValueExtractionByOperationPath>()
    private val snakeCaseNameTransformer: (String) -> String by lazy {
        val cache: ConcurrentMap<String, String> = ConcurrentHashMap()
        val strategy: SnakeCaseStrategy = SnakeCaseStrategy();
        { name: String -> cache.computeIfAbsent(name, strategy::translate) }
    }

    override fun invoke(jsonValue: JsonNode, pathToExtract: GQLOperationPath): Option<JsonNode> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "invoke: [ json_value.node_type: {}, path_to_extract: {} ]",
                jsonValue.nodeType,
                pathToExtract
            )
        }
        return when {
            pathToExtract.isRoot() -> {
                jsonValue.toOption()
            }
            !pathToExtract.refersToSelection() -> {
                none<JsonNode>()
            }
            else -> {
                traverseJsonNodeUntilPathTraversed(
                    jsonValue,
                    pathToExtract.selection.toPersistentList()
                )
            }
        }
    }

    private data class TraversalContext(
        val node: JsonNode,
        val remaining: PersistentList<SelectionSegment>
    )

    private fun traverseJsonNodeUntilPathTraversed(
        topLevelJsonNode: JsonNode,
        selections: PersistentList<SelectionSegment>
    ): Option<JsonNode> {
        return sequenceOf(TraversalContext(node = topLevelJsonNode, remaining = selections))
            .recurseBreadthFirst { c: TraversalContext ->
                extractHeadSelectionsOrReturnSelectedNodes(c)
            }
            .fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add)
            .let { an: ArrayNode ->
                when (an.size()) {
                    0 -> {
                        none()
                    }
                    1 -> {
                        an.get(0).toOption()
                    }
                    else -> {
                        an.some()
                    }
                }
            }
    }

    private fun extractHeadSelectionsOrReturnSelectedNodes(
        traversalContext: TraversalContext
    ): Sequence<Either<TraversalContext, JsonNode>> {
        require(traversalContext.remaining.isNotEmpty()) {
            "%s.remaining must not be empty for this method"
                .format(TraversalContext::class::simpleName)
        }
        val headSelectionSegment: SelectionSegment = traversalContext.remaining[0]
        val updatedList: PersistentList<SelectionSegment> = traversalContext.remaining.removeAt(0)
        return when {
            updatedList.isEmpty() -> {
                selectHeadSelectionSegmentOnNode(headSelectionSegment, traversalContext.node).map {
                    j: JsonNode ->
                    j.right()
                }
            }
            else -> {
                selectHeadSelectionSegmentOnNode(headSelectionSegment, traversalContext.node).map {
                    j: JsonNode ->
                    TraversalContext(j, updatedList).left()
                }
            }
        }
    }

    private fun selectHeadSelectionSegmentOnNode(
        segment: SelectionSegment,
        node: JsonNode
    ): Sequence<JsonNode> {
        val fieldNameToUse: String = extractFieldNameToUse(segment)
        return when (node) {
            is ArrayNode -> {
                node
                    .asSequence()
                    .filterIsInstance<ObjectNode>()
                    .map { on: ObjectNode ->
                        when (val n: JsonNode = on.path(fieldNameToUse)) {
                            is MissingNode -> {
                                on.path(snakeCaseNameTransformer(fieldNameToUse))
                            }
                            else -> {
                                n
                            }
                        }
                    }
                    .filterNot { j: JsonNode -> j.isMissingNode }
            }
            is ObjectNode -> {
                when (val n: JsonNode = node.path(fieldNameToUse)) {
                    is MissingNode -> {
                        when (
                            val n1: JsonNode = node.path(snakeCaseNameTransformer(fieldNameToUse))
                        ) {
                            is MissingNode -> {
                                emptySequence<JsonNode>()
                            }
                            else -> {
                                sequenceOf(n1)
                            }
                        }
                    }
                    else -> {
                        sequenceOf(n)
                    }
                }
            }
            else -> {
                emptySequence<JsonNode>()
            }
        }
    }

    private fun extractFieldNameToUse(segment: SelectionSegment): String {
        return when (segment) {
            is FieldSegment -> {
                segment.fieldName
            }
            is AliasedFieldSegment -> {
                segment.alias
            }
            is FragmentSpreadSegment -> {
                when (segment.selectedField) {
                    is AliasedFieldSegment -> {
                        segment.selectedField.alias
                    }
                    is FieldSegment -> {
                        segment.selectedField.fieldName
                    }
                }
            }
            is InlineFragmentSegment -> {
                when (segment.selectedField) {
                    is AliasedFieldSegment -> {
                        segment.selectedField.alias
                    }
                    is FieldSegment -> {
                        segment.selectedField.fieldName
                    }
                }
            }
        }
    }
}
