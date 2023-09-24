package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import arrow.core.toOption
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.iterator.MultiValueMapSingleValueEntryCombinationsIterator
import java.util.*

object SequenceExtensions {

    fun <T, O : Option<T>> Sequence<O>.flatMapOptions(): Sequence<T> {
        return this.flatMap { opt: Option<T> -> opt.fold(::emptySequence, ::sequenceOf) }
    }

    fun <T> Sequence<T>.firstOrNone(): Option<T> {
        return this.firstOrNull().toOption()
    }

    fun <T> Sequence<T>.firstOrNone(condition: (T) -> Boolean): Option<T> {
        return this.firstOrNull(condition).toOption()
    }

    fun <L, R> Sequence<L>.recurse(function: (L) -> Sequence<Either<L, R>>): Sequence<R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithSequence(l, function) }
    }

    fun <K, V> Sequence<Map.Entry<K, V>>.singleValueMapCombinationsFromEntries():
        Sequence<Map<K, V>> {
        return Sequence {
            MultiValueMapSingleValueEntryCombinationsIterator(
                this.groupBy(Map.Entry<K, V>::key, Map.Entry<K, V>::value)
            )
        }
    }

    fun <K, V> Sequence<Pair<K, V>>.singleValueMapCombinationsFromPairs(): Sequence<Map<K, V>> {
        return Sequence {
            MultiValueMapSingleValueEntryCombinationsIterator(
                this.groupBy(Pair<K, V>::first, Pair<K, V>::second)
            )
        }
    }

}
