package funcify.feature.schema.json

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.recurseBreadthFirst
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object JsonNodeToNodesByResultPathsExtractor : (JsonNode) -> ImmutableMap<GQLResultPath, JsonNode> {

    override fun invoke(dataJsonObject: JsonNode): ImmutableMap<GQLResultPath, JsonNode> {
        return sequenceOf(JsonNodePathTraversalContext(persistentListOf(), dataJsonObject))
            .recurseBreadthFirst { c: JsonNodePathTraversalContext ->
                traverseJsonNodeContextCreatingPathsForEachNonObjectValue(c)
            }
            .sortedBy { (rp, _) -> rp }
            .reducePairsToPersistentMap()
    }

    private data class JsonNodePathTraversalContext(
        val nameOrIndex: PersistentList<Either<String, Int>>,
        val value: JsonNode
    )

    private val nameOrIndexListToResultPathCalculator:
        (ImmutableList<Either<String, Int>>) -> GQLResultPath by lazy {
        val cache: ConcurrentMap<ImmutableList<Either<String, Int>>, GQLResultPath> =
            ConcurrentHashMap();
        { namesOrIndices: ImmutableList<Either<String, Int>> ->
            cache.computeIfAbsent(namesOrIndices, ::nameOrIndexToResultPath)
        }
    }

    private fun traverseJsonNodeContextCreatingPathsForEachNonObjectValue(
        context: JsonNodePathTraversalContext
    ): Sequence<Either<JsonNodePathTraversalContext, Pair<GQLResultPath, JsonNode>>> {
        return when (context.value.nodeType) {
            JsonNodeType.NULL,
            JsonNodeType.MISSING -> {
                // Case 1: Replace missing with null nodes
                sequenceOf(
                    (nameOrIndexListToResultPathCalculator(context.nameOrIndex) to
                            JsonNodeFactory.instance.nullNode())
                        .right()
                )
            }
            JsonNodeType.BOOLEAN,
            JsonNodeType.NUMBER,
            JsonNodeType.BINARY,
            JsonNodeType.STRING -> {
                sequenceOf(
                    (nameOrIndexListToResultPathCalculator(context.nameOrIndex) to context.value)
                        .right()
                )
            }
            JsonNodeType.ARRAY -> {
                sequenceOf(
                        (nameOrIndexListToResultPathCalculator(context.nameOrIndex) to
                                context.value)
                            .right()
                    )
                    .plus(
                        context.value.asSequence().withIndex().map { (idx: Int, jn: JsonNode) ->
                            JsonNodePathTraversalContext(
                                    nameOrIndex = context.nameOrIndex.add(idx.right()),
                                    value = jn
                                )
                                .left()
                        }
                    )
            }
            JsonNodeType.OBJECT -> {
                sequenceOf(
                        (nameOrIndexListToResultPathCalculator(context.nameOrIndex) to
                                context.value)
                            .right()
                    )
                    .plus(
                        context.value.fields().asSequence().map { (name: String, value: JsonNode) ->
                            JsonNodePathTraversalContext(
                                    nameOrIndex = context.nameOrIndex.add(name.left()),
                                    value = value
                                )
                                .left()
                        }
                    )
            }
            else -> {
                emptySequence()
            }
        }
    }

    private fun nameOrIndexToResultPath(
        namesOrIndices: ImmutableList<Either<String, Int>>
    ): GQLResultPath {
        return GQLResultPath.getRootPath().transform {
            var lastNameSegment: Int = -1
            var i: Int = 0
            while (i < namesOrIndices.size) {
                when (val nOrI = namesOrIndices[i]) {
                    is Either.Left<String> -> {
                        when {
                            lastNameSegment >= 0 && i - lastNameSegment == 1 -> {
                                appendNameSegment(namesOrIndices[lastNameSegment].swap().orNull()!!)
                            }
                            lastNameSegment >= 0 && i - lastNameSegment > 1 -> {
                                appendNestedListSegment(
                                    namesOrIndices[lastNameSegment].swap().orNull()!!,
                                    namesOrIndices
                                        .subList(lastNameSegment + 1, i)
                                        .asSequence()
                                        .mapNotNull(Either<String, Int>::orNull)
                                        .toList()
                                )
                            }
                            else -> {}
                        }
                        lastNameSegment = i
                    }
                    is Either.Right<Int> -> {}
                }
                i++
            }
            when {
                lastNameSegment >= 0 && i - lastNameSegment == 1 -> {
                    appendNameSegment(namesOrIndices[lastNameSegment].swap().orNull()!!)
                }
                lastNameSegment >= 0 && i - lastNameSegment > 1 -> {
                    appendNestedListSegment(
                        namesOrIndices[lastNameSegment].swap().orNull()!!,
                        namesOrIndices
                            .subList(lastNameSegment + 1, i)
                            .asSequence()
                            .mapNotNull(Either<String, Int>::orNull)
                            .toList()
                    )
                }
                else -> {
                    this
                }
            }
        }
    }
}
