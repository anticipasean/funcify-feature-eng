package funcify.feature.tools.extensions

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Success
import funcify.feature.tools.container.attempt.Try
import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Mono

object MonoExtensions {

    private val NULL_RESULT_EXCEPTION: IllegalArgumentException by lazy {
        IllegalArgumentException(
            "%s.result must be non-null but given result is null".format(
                Success::class.qualifiedName
            )
        )
    }

    fun <T> Mono<T>?.toTry(): Try<T> {
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

    fun <T> Mono<T>?.toKFuture(): KFuture<T> {
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
    fun <N : W, W> Mono<N>.widen(): Mono<W> {
        return try {
            @Suppress("UNCHECKED_CAST") //
            this as Mono<W>
        } catch (cce: ClassCastException) {
            this.map { n -> n }
        }
    }
}
