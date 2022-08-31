package funcify.feature.tools.extensions

import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Mono

object KFutureExtensions {

    fun <T> Mono<T?>?.toKFuture(): KFuture<T> {
        return when {
            this == null -> {
                KFuture.emptyCompleted<T>().map { tOpt -> Try.fromOption(tOpt).orElseThrow() }
            }
            else -> {
                KFuture.of(
                    this.toFuture().thenApply { t -> Try.fromOption(t.toOption()).orElseThrow() }
                )
            }
        }
    }
}
