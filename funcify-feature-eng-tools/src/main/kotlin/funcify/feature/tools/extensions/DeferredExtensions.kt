package funcify.feature.tools.extensions

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import java.util.concurrent.CompletionStage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object DeferredExtensions {

    fun <S> Try<S>.toDeferred(): Deferred<S> {
        return this.fold(Deferred.Companion::completed, Deferred.Companion::failed)
    }

    fun <T> CompletionStage<T>.toDeferred(): Deferred<T> {
        return Deferred.fromCompletionStage(this)
    }

    fun <T> KFuture<T>.toDeferred(): Deferred<T> {
        return Deferred.fromKFuture(this)
    }

    fun <T> Mono<T>.toDeferred(): Deferred<T> {
        return Deferred.fromMono(this)
    }

    fun <T> Flux<T>.toDeferred(): Deferred<T> {
        return Deferred.fromFlux(this)
    }
}
