package funcify.feature.tree

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tree.context.StandardTreeContext
import funcify.feature.tree.path.IndexSegment
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.PathSegment
import funcify.feature.tree.path.TreePath
import funcify.feature.tree.spliterator.TreeBreadthFirstSearchSpliterator
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.streams.asSequence

/**
 *
 * @author smccarron
 * @created 2023-04-05
 */
interface PersistentTree<out V> : ImmutableTree<V> {

    companion object {

        fun <V> empty(): EmptyTree<V> {
            return StandardTreeContext.empty()
        }

        fun <V> fromSequence(sequence: Sequence<Pair<TreePath, V>>): PersistentTree<V> {
            return StandardTreeContext.empty<V>().fromSequence(sequence)
        }

        fun <V> fromStream(stream: Stream<out Pair<TreePath, V>>): PersistentTree<V> {
            return fromSequence(stream.asSequence())
        }

        /**
         * ## Example:
         * ```
         *  PersistentTree.fromSequenceFunctionOnValue<JsonNode>(rootNode) { jn: JsonNode ->
         *    when (jn) {
         *        is ArrayNode -> {
         *            // Associate these json_nodes with indices => IndexSegment
         *            jn.asSequence().map { indexedValue: JsonNode -> indexedValue.left() }
         *        }
         *        is ObjectNode -> {
         *            // Associate these json_nodes with names => NameSegment
         *            jn.fields().asSequence().map { (name: String, value: JsonNode) -> (name to value).right() }
         *        }
         *        else -> {
         *            // Only scalar nodes that are part of an array or object node; exclude a root level scalar
         *            emptySequence()
         *        }
         *    }
         *  }
         * ```
         */
        fun <V> fromSequenceFunctionOnValue(
            startValue: V,
            function: (V) -> Sequence<Either<V, Pair<String, V>>>
        ): PersistentTree<V> {
            val valueToStream: (V) -> Stream<Pair<PathSegment, V>> = { v: V ->
                StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(
                        function
                            .invoke(v)
                            .withIndex()
                            .map { (idx: Int, e: Either<V, Pair<String, V>>) ->
                                e.fold(
                                    { cv: V -> IndexSegment(idx) to cv },
                                    { (name: String, cv: V) -> NameSegment(name) to cv }
                                )
                            }
                            .iterator(),
                        0
                    ),
                    false
                )
            }
            return fromStream(
                StreamSupport.stream(
                    TreeBreadthFirstSearchSpliterator<V>(
                        TreePath.getRootPath(),
                        startValue,
                        valueToStream
                    ),
                    false
                )
            )
        }

        fun <V> fromStreamFunctionOnValue(
            startValue: V,
            function: (V) -> Stream<Either<V, Pair<String, V>>>
        ): PersistentTree<V> {
            return fromSequenceFunctionOnValue(startValue) { v: V ->
                function.invoke(v).asSequence()
            }
        }
    }

    override operator fun get(path: TreePath): Option<PersistentTree<V>>

    override fun descendentsUnder(path: TreePath): Iterable<PersistentTree<V>>

    override fun size(): Int

    override fun children(): Iterable<PersistentTree<V>>

    override fun <V1> map(function: (V) -> V1): PersistentTree<V1>

    override fun <V1> bimap(function: (TreePath, V) -> Pair<TreePath, V1>): PersistentTree<V1>

    override fun <V1> bimap(
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1
    ): PersistentTree<V1>

    override fun filter(condition: (V) -> Boolean): PersistentTree<V>

    override fun biFilter(condition: (TreePath, V) -> Boolean): PersistentTree<V>

    override fun <V1> flatMap(function: (V) -> ImmutableTree<V1>): PersistentTree<V1>

    override fun <V1> biFlatMap(function: (TreePath, V) -> ImmutableTree<V1>): PersistentTree<V1>

    override fun <V1, V2> zip(other: ImmutableTree<V1>, function: (V, V1) -> V2): PersistentTree<V2>

    override fun <V1, V2> biZip(
        other: ImmutableTree<V1>,
        function: (Pair<TreePath, V>, Pair<TreePath, V1>) -> Pair<TreePath, V2>,
    ): PersistentTree<V2>

    fun <R> fold(emptyHandler: (EmptyTree<V>) -> R, nonEmptyHandler: (NonEmptyTree<V>) -> R): R
}
