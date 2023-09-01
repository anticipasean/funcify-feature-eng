package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.none
import funcify.feature.tools.control.TraversalFunctions
import java.util.*
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 4/5/22
 */
object OptionExtensions {

    fun <T> Option<T>.sequence(): Sequence<T> {
        return this.fold(::emptySequence, ::sequenceOf)
    }

    fun <T> Option<T>.stream(): Stream<out T> {
        return this.fold({ Stream.empty() }, { t -> Stream.ofNullable(t) })
    }

    fun <L, R> Option<L>.recurse(function: (L) -> Option<Either<L, R>>): Option<R> {
        return this.fold({ none() }, { l: L -> TraversalFunctions.recurseWithOption(l, function) })
    }

    fun <T> Option<T>.toPersistentSet(): PersistentSet<T> {
        return this.fold({ persistentSetOf() }, { t: T -> persistentSetOf(t) })
    }

    fun <T> Optional<T?>?.toOption(): Option<T> {
        return when (this) {
            null -> {
                None
            }
            else -> {
                when (val t: T? = this.orElse(null)) {
                    null -> None
                    else -> Some<T>(t)
                }
            }
        }
    }

    fun <T> Option<T?>?.toMono(): Mono<T> {
        return when (this) {
            null -> {
                Mono.empty<T>()
            }
            else -> {
                when (val t: T? = this.orNull()) {
                    null -> Mono.empty<T>()
                    else -> Mono.just(t)
                }
            }
        }
    }
}
