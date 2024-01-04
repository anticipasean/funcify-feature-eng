package funcify.feature.schema.json

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.lastOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.schema.path.result.ElementSegment
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.PairExtensions.unfold
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-1-3
 */
object GQLResultValuesToJsonNodeConverter : (Map<GQLResultPath, JsonNode>) -> JsonNode {

    private const val METHOD_TAG: String = "gqlresult_values_to_json_node_converter.invoke"
    private const val TERMINAL_INDEX: Int = -1
    private val logger: Logger = loggerFor<GQLResultValuesToJsonNodeConverter>()

    override fun invoke(gqlResultValues: Map<GQLResultPath, JsonNode>): JsonNode {
        logger.info("{}: [ gql_result_values.size: {} ]", METHOD_TAG, gqlResultValues.size)
        return gqlResultValues
            .asSequence()
            .fold(
                persistentMapOf<Int, PersistentSet<GQLResultPath>>(),
                ::updateResultPathsByLevelWithEntry
            )
            .let { rpbl: PersistentMap<Int, PersistentSet<GQLResultPath>> ->
                createJsonNodeFromResultPathsByLevel(rpbl, gqlResultValues)
            }
    }

    private fun updateResultPathsByLevelWithEntry(
        resultPathsByLevel: PersistentMap<Int, PersistentSet<GQLResultPath>>,
        entry: Map.Entry<GQLResultPath, JsonNode>
    ): PersistentMap<Int, PersistentSet<GQLResultPath>> {
        return (resultPathsByLevel to entry)
            .toOption()
            .map { (rpbl, e) ->
                rpbl.put(
                    e.key.elementSegments.size,
                    rpbl.getOrElse(e.key.elementSegments.size, ::persistentSetOf).add(e.key)
                ) to e.key
            }
            .recurse { (rpbl, p) ->
                when (
                    val pp: GQLResultPath? =
                        p.getParentPath()
                            .filterNot { pp: GQLResultPath ->
                                rpbl
                                    .getOrElse(pp.elementSegments.size, ::persistentSetOf)
                                    .contains(pp)
                            }
                            .orNull()
                ) {
                    null -> {
                        rpbl.right().some()
                    }
                    else -> {
                        (rpbl.put(
                                pp.elementSegments.size,
                                rpbl.getOrElse(pp.elementSegments.size, ::persistentSetOf).add(pp)
                            ) to pp)
                            .left()
                            .some()
                    }
                }
            }
            .getOrElse { resultPathsByLevel }
    }

    private fun createJsonNodeFromResultPathsByLevel(
        resultPathsByLevel: PersistentMap<Int, PersistentSet<GQLResultPath>>,
        gqlResultValues: Map<GQLResultPath, JsonNode>
    ): JsonNode {
        return resultPathsByLevel
            .asSequence()
            .sortedByDescending { (level: Int, _: PersistentSet<GQLResultPath>) -> level }
            .filterNot { (level: Int, _: PersistentSet<GQLResultPath>) -> level == 0 }
            .fold(persistentMapOf<GQLResultPath, JsonNode>()) {
                resultMap: PersistentMap<GQLResultPath, JsonNode>,
                (_: Int, resultPathSetAtLevel: PersistentSet<GQLResultPath>) ->
                resultPathSetAtLevel
                    .asSequence()
                    .groupBy { p -> p.getParentPath().getOrElse { GQLResultPath.getRootPath() } }
                    .asSequence()
                    .map { (parentPath, childPathSet) ->
                        createParentJsonNodeForChildPathSet(
                            parentPath,
                            childPathSet,
                            resultMap,
                            gqlResultValues
                        )
                    }
                    .reducePairsToPersistentMap()
            }
            .let { resultMap: PersistentMap<GQLResultPath, JsonNode> ->
                resultMap.getOrNone(GQLResultPath.getRootPath()).getOrElse {
                    JsonNodeFactory.instance.nullNode()
                }
            }
    }

    private fun createParentJsonNodeForChildPathSet(
        parentPath: GQLResultPath,
        childPathSet: List<GQLResultPath>,
        previousLevelResults: Map<GQLResultPath, JsonNode>,
        gqlResultValues: Map<GQLResultPath, JsonNode>,
    ): Pair<GQLResultPath, JsonNode> {
        return childPathSet
            .asSequence()
            .filter { p -> p.elementSegments.size >= 1 }
            .groupBy { p ->
                /**
                 * All child paths with zero element segments, which should not have been passed to
                 * this method, should be filtered out in previous transformer so non-null assertion
                 * should not throw error
                 */
                p.elementSegments
                    .lastOrNone()
                    .map { es: ElementSegment ->
                        when (es) {
                            is ListSegment -> es.name
                            is NameSegment -> es.name
                        }
                    }
                    .orNull()!!
            }
            .asSequence()
            .map { (name: String, relatedPaths: List<GQLResultPath>) ->
                when {
                    relatedPaths
                        .firstOrNone()
                        .filter { p ->
                            p.elementSegments
                                .lastOrNone()
                                .filterIsInstance<ListSegment>()
                                .isDefined()
                        }
                        .isDefined() -> {
                        createArrayNodeForChildPaths(
                            name,
                            relatedPaths,
                            previousLevelResults,
                            gqlResultValues
                        )
                    }
                    else -> {
                        relatedPaths.firstOrNone().flatMap { rp: GQLResultPath ->
                            previousLevelResults
                                .getOrNone(rp)
                                .orElse { gqlResultValues.getOrNone(rp) }
                                .map { jn: JsonNode -> name to jn }
                        }
                    }
                }
            }
            .flatMapOptions()
            .fold(JsonNodeFactory.instance.objectNode()) { on: ObjectNode, (k: String, v: JsonNode)
                ->
                on.set(k, v)
            }
            .let { on: ObjectNode -> parentPath to on }
    }

    private fun createArrayNodeForChildPaths(
        name: String,
        relatedPaths: List<GQLResultPath>,
        previousLevelResults: Map<GQLResultPath, JsonNode>,
        gqlResultValues: Map<GQLResultPath, JsonNode>,
    ): Option<Pair<String, JsonNode>> {
        return relatedPaths
            .asSequence()
            .map { rp: GQLResultPath ->
                rp.elementSegments.lastOrNone().map { es: ElementSegment -> es to rp }
            }
            .flatMapOptions()
            .map { (es: ElementSegment, rp: GQLResultPath) ->
                when (es) {
                    is ListSegment -> Option(es to rp)
                    is NameSegment -> none()
                }
            }
            .flatMapOptions()
            .map { (ls: ListSegment, rp: GQLResultPath) ->
                previousLevelResults
                    .getOrNone(rp)
                    .orElse { gqlResultValues.getOrNone(rp) }
                    .map { jn: JsonNode -> ls to jn }
            }
            .flatMapOptions()
            .fold(persistentMapOf(), ::updateIndicesByLevel)
            .asSequence()
            .sortedByDescending { (level, _) -> level }
            .fold(persistentMapOf<Int, JsonNode>()) { prevLevelNodes, (_, currentLevelNodes) ->
                currentLevelNodes
                    .asSequence()
                    .groupBy({ e -> e.key.first }, { e -> e.key.second to e.value })
                    .asSequence()
                    .map { (parent, children) ->
                        parent to
                            children
                                .asSequence()
                                .sortedBy { (ck, _) -> ck }
                                .map { (ck, cv) -> prevLevelNodes.getOrNone(ck).orElse { cv } }
                                .flatMapOptions()
                                .fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add)
                    }
                    .fold(persistentMapOf<Int, JsonNode>(), PersistentMap<Int, JsonNode>::plus)
            }
            .let { resultNodes: PersistentMap<Int, JsonNode> ->
                resultNodes.getOrNone(TERMINAL_INDEX).map { jn: JsonNode -> name to jn }
            }
    }

    private fun updateIndicesByLevel(
        indicesByLevel: PersistentMap<Int, PersistentMap<Pair<Int, Int>, Option<JsonNode>>>,
        pair: Pair<ListSegment, JsonNode>,
    ): PersistentMap<Int, PersistentMap<Pair<Int, Int>, Option<JsonNode>>> {
        return (indicesByLevel to pair)
            .toOption()
            .filter { (_, p) -> p.first.indices.size >= 1 }
            .map { (ibl, p) ->
                val parentChild: Pair<Int, Int> =
                    p.first.indices.unfold(
                        { i ->
                            when (i.size) {
                                1 -> TERMINAL_INDEX
                                else -> i[i.size - 2]
                            }
                        },
                        { i -> i.last() }
                    )
                ibl.put(
                    p.first.indices.size,
                    ibl.getOrElse(p.first.indices.size, ::persistentMapOf)
                        .put(parentChild, p.second.some())
                ) to p.first.indices
            }
            .recurse { (ibl, ind) ->
                when {
                    ind.size <= 1 -> {
                        ibl.right().some()
                    }
                    else -> {
                        val parent: PersistentList<Int> = ind.removeAt(ind.lastIndex)
                        val parentChild: Pair<Int, Int> =
                            parent.unfold(
                                { i ->
                                    when (i.size) {
                                        1 -> TERMINAL_INDEX
                                        else -> i[i.size - 2]
                                    }
                                },
                                { i -> i.last() }
                            )
                        val parentSize: Int = parent.size
                        when {
                            parentSize == 0 -> {
                                ibl.right().some()
                            }
                            parentSize in ibl &&
                                ibl[parentSize]?.containsKey(parentChild) == true -> {
                                ibl.right().some()
                            }
                            else -> {
                                (ibl.put(
                                        parentSize,
                                        ibl.getOrElse(parentSize, ::persistentMapOf)
                                            .put(parentChild, none())
                                    ) to parent)
                                    .left()
                                    .some()
                            }
                        }
                    }
                }
            }
            .getOrElse { indicesByLevel }
    }
}
