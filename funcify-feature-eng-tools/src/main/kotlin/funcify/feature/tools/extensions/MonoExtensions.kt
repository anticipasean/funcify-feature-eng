package funcify.feature.tools.extensions

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object MonoExtensions {

    private val NULL_RESULT_EXCEPTION: IllegalArgumentException by lazy {
        IllegalArgumentException(
            "%s.result must be non-null but given result is null"
                .format(Success::class.qualifiedName)
        )
    }

    fun <T> Mono<out T>?.toTry(): Try<T> {
        return when (this) {
            null -> {
                Try.failure<T>(NULL_RESULT_EXCEPTION)
            }
            else -> {
                try {
                    when (val result: T? = this.block()) {
                        null -> {
                            Try.failure<T>(NULL_RESULT_EXCEPTION)
                        }
                        else -> {
                            Try.success(result)
                        }
                    }
                } catch (t: Throwable) {
                    Try.failure<T>(t)
                }
            }
        }
    }

    fun <T> Mono<out T>?.toKFuture(): KFuture<T> {
        return when {
            this == null -> {
                KFuture.failed(NoSuchElementException("${Mono::class.qualifiedName} is null"))
            }
            else -> {
                KFuture.of(
                    this.toFuture().thenComposeAsync { t ->
                        when (t) {
                            null ->
                                CompletableFuture.failedFuture(
                                    NoSuchElementException(
                                        "input from ${Mono::class.qualifiedName} is null"
                                    )
                                )
                            else -> {
                                CompletableFuture.completedFuture(t)
                            }
                        }
                    }
                )
            }
        }
    }

    /** @return [Mono<W>] where W is a widened type from the narrow N */
    fun <N : W, W> Mono<out N>.widen(): Mono<W> {
        @Suppress("UNCHECKED_CAST") //
        return this as Mono<W>
    }

    inline fun <reified T, reified R> Sequence<T>?.foldIntoMono(
        initial: R,
        crossinline accumulator: (R, T) -> R
    ): Mono<out R> {
        return Flux.fromIterable(this?.asIterable() ?: emptyList()).reduce(initial) { r: R, t: T ->
            accumulator.invoke(r, t)
        }
    }

    inline fun <reified T, reified R> Sequence<T>?.foldMapIntoMono(
        initial: R,
        crossinline accumulator: (R, T) -> Mono<out R>
    ): Mono<out R> {
        return (this ?: emptySequence()).fold(Mono.just(initial)) { rPub: Mono<out R>, t: T ->
            rPub.flatMap { r: R -> accumulator.invoke(r, t) }
        }
    }

    inline fun <reified T> Mono<out Mono<out T>>?.flatten(): Mono<out T> {
        return when {
            this == null -> {
                Mono.empty<T>()
            }
            else -> {
                this.flatMap { tPub: Mono<out T> -> tPub }
            }
        }
    }

    /**
     * Cast required here due to apparent incompatibility with Java and Kotlin type systems on this
     * onErrorResume function parameter signature:
     * ```
     * Function<? super Throwable, ? extends Mono<? extends T>> fallback
     * ```
     */
    fun <T> Mono<out T>.flatMapError(transformer: (Throwable) -> Mono<out T>): Mono<out T> {
        return this.onErrorResume { t: Throwable ->
            @Suppress("UNCHECKED_CAST")
            transformer.invoke(t) as Mono<Nothing>
        }
    }

    fun <T> Mono<out T>.filter(
        condition: (T) -> Boolean,
        onConditionUnmet: (T) -> Throwable
    ): Mono<out T> {
        return this.flatMap { t: T ->
            if (condition.invoke(t)) {
                Mono.just(t)
            } else {
                Mono.error(onConditionUnmet(t))
            }
        }
    }
}
