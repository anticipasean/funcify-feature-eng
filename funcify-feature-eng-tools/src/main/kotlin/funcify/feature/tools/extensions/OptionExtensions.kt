package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import java.util.*
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.Stream.ofNullable
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
        return this.fold(::empty, ::ofNullable)
    }

    /**
     * Calls the input function of type `(L) -> Option<Either<L, R>>` on any `Either.Left<L>>` value
     * until `Option.None` or a `Option.Some(Either.Right<R>)` value is returned. This call will
     * _NOT_ terminate if the such a function _never_ returns `Option.None` or a
     * `Option.Some(Either.Right<R>))` value. The caller must ensure that the input function
     * contains some sort of terminal condition Note on naming: I have named this `recurse` instead
     * of its more common name `traverse` in order to avoid clashes with the arrow.core API
     */
    fun <L, R> Option<L>.recurse(function: (L) -> Option<Either<L, R>>): Option<R> {
        return this.flatMap { l: L ->
            val resultHolder: Array<Option<Either<L, R>>> = arrayOf(Some(Either.Left(l)))
            var continueLoop: Boolean
            do {
                continueLoop =
                    when (val eOpt: Option<Either<L, R>> = resultHolder[0]) {
                        is None -> {
                            false
                        }
                        is Some<Either<L, R>> -> {
                            when (val e: Either<L, R> = eOpt.value) {
                                is Either.Left<L> -> {
                                    resultHolder[0] = function.invoke(e.value)
                                    true
                                }
                                is Either.Right<R> -> {
                                    false
                                }
                            }
                        }
                    }
            } while (continueLoop)
            resultHolder[0].flatMap { e: Either<L, R> ->
                when (e) {
                    is Either.Left<L> -> {
                        None
                    }
                    is Either.Right<R> -> {
                        Some(e.value)
                    }
                }
            }
        }
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

    fun <T : Any?> Option<T>?.toMono(): Mono<out T> {
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
