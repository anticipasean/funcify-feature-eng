package funcify.feature.tools.extensions

import funcify.feature.tools.container.async.KFuture
import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Mono

object MonoExtensions {

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
}
