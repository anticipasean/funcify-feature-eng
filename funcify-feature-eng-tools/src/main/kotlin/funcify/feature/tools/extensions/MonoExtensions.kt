package funcify.feature.tools.extensions

import arrow.core.identity
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Mono

object MonoExtensions {

    fun <T> Mono<T>?.toTry(): Try<T> {
        return when {
            this == null -> {
                Try.nullableSuccess<T>(null)
            }
            else -> {
                this.toKFuture().get()
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

    fun <T> Mono<out T>.widen(): Mono<T> {
        return this.map(::identity)
    }
}
