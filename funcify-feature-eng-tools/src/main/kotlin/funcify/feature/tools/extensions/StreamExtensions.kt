package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.iterator.MultiValueMapSingleValueEntryCombinationsSpliterator
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport

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

    fun <L, R> Stream<out L>.recurse(function: (L) -> Stream<out Either<L, R>>): Stream<out R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithStream(l, function) }
    }

    fun <T> Stream<out T>.asIterable(): Iterable<T> {
        return Iterable<T> { this.iterator() }
    }

    fun <K, V> Stream<out Map.Entry<K, V>>.singleValueMapCombinationsFromEntries():
        Stream<out Map<K, V>> {
        return StreamSupport.stream(
            MultiValueMapSingleValueEntryCombinationsSpliterator(
                this.collect(
                    Collectors.groupingBy(
                        Map.Entry<K, V>::key,
                        Collectors.mapping(Map.Entry<K, V>::value, Collectors.toList())
                    )
                )
            ),
            false
        )
    }

    fun <K, V> Stream<out Pair<K, V>>.singleValueMapCombinationsFromPairs(): Stream<out Map<K, V>> {
        return StreamSupport.stream(
            MultiValueMapSingleValueEntryCombinationsSpliterator(
                this.collect(
                    Collectors.groupingBy(
                        Pair<K, V>::first,
                        Collectors.mapping(Pair<K, V>::second, Collectors.toList())
                    )
                )
            ),
            false
        )
    }
}
