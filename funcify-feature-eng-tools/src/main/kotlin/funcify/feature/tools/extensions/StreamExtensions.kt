package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tools.extensions.CollectorsExtensions.toPersistentList
import funcify.feature.tools.extensions.CollectorsExtensions.toPersistentSet
import funcify.feature.tools.extensions.FunctionExtensions.andThen
import funcify.feature.tools.spliterator.BreadthFirstEitherRecursiveSpliterator
import funcify.feature.tools.spliterator.DepthFirstEitherRecursiveSpliterator
import funcify.feature.tools.spliterator.MultiValueMapSingleValueEntryCombinationsSpliterator
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

object StreamExtensions {

    fun <T, O : Option<T>> Stream<out O>.flatMapOptions(): Stream<out T> {
        return this.flatMap { opt: Option<T> ->
            opt.fold({ Stream.empty() }, { t: T -> Stream.ofNullable(t) })
        }
    }

    inline fun <reified T : Any> Stream<*>.filterIsInstance(): Stream<out T> {
        return this.flatMap { input: Any? ->
            when (input) {
                is T -> Stream.of(input)
                else -> Stream.empty()
            }
        }
    }

    fun <L, R> Stream<out L>.recurseBreadthFirst(
        function: (L) -> Stream<out Either<L, R>>,
    ): Stream<out R> {
        return this.flatMap { l: L ->
            StreamSupport.stream(
                BreadthFirstEitherRecursiveSpliterator<L, R>(
                    initialLeftValue = l,
                    traversalFunction =
                        function.andThen { s: Stream<out Either<L, R>> -> s.iterator() }
                ),
                false
            )
        }
    }

    fun <L, R> Stream<out L>.recurseDepthFirst(
        function: (L) -> Stream<out Either<L, R>>,
    ): Stream<out R> {
        return this.flatMap { l: L ->
            StreamSupport.stream(
                DepthFirstEitherRecursiveSpliterator<L, R>(
                    initialLeftValue = l,
                    traversalFunction =
                        function.andThen { s: Stream<out Either<L, R>> -> s.iterator() }
                ),
                false
            )
        }
    }

    fun <T> Stream<out T>.asIterable(): Iterable<T> {
        return Iterable<T> { this.iterator() }
    }

    fun <K, V> Stream<out Map.Entry<K, V>>.singleValueMapCombinationsFromEntries():
        Stream<out Map<K, V>> {
        return StreamSupport.stream(
            MultiValueMapSingleValueEntryCombinationsSpliterator(
                this.groupIntoMap(Map.Entry<K, V>::key, Map.Entry<K, V>::value)
            ),
            false
        )
    }

    fun <K, V> Stream<out Pair<K, V>>.singleValueMapCombinationsFromPairs(): Stream<out Map<K, V>> {
        return StreamSupport.stream(
            MultiValueMapSingleValueEntryCombinationsSpliterator(
                this.groupIntoMap(Pair<K, V>::first, Pair<K, V>::second)
            ),
            false
        )
    }

    fun <T, K, V> Stream<T>.groupIntoMap(
        keySelector: (T) -> K,
        valueSelector: (T) -> V,
    ): MutableMap<K, MutableList<V>> {
        return this.collect(
            Collectors.groupingBy(
                keySelector,
                ::mutableMapOf,
                Collectors.mapping(valueSelector, Collectors.toList())
            )
        )
    }

    fun <T, K, V> Stream<T>.groupIntoPersistentMap(
        keySelector: (T) -> K,
        valueSelector: (T) -> V,
    ): PersistentMap<K, PersistentList<V>> {
        return this.collect(
                Collectors.groupingBy(
                    keySelector,
                    { persistentMapOf<K, PersistentList<V>>().builder() },
                    Collectors.mapping(valueSelector, toPersistentList())
                )
            )
            .build()
    }

    fun <T, K, V> Stream<T>.groupIntoPersistentSet(
        keySelector: (T) -> K,
        valueSelector: (T) -> V,
    ): PersistentMap<K, PersistentSet<V>> {
        return this.collect(
                Collectors.groupingBy(
                    keySelector,
                    { persistentMapOf<K, PersistentSet<V>>().builder() },
                    Collectors.mapping(valueSelector, toPersistentSet())
                )
            )
            .build()
    }
}
