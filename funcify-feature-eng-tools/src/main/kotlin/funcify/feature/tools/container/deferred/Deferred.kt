package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import java.util.concurrent.CompletionStage
import reactor.core.publisher.Mono

interface Deferred<out V> {

    companion object {

        internal val deferredManyTemplate: DeferredManyTemplate = object : DeferredManyTemplate {}

        internal val deferredSingleTemplate: DeferredSingleTemplate = object : DeferredSingleTemplate {}

        fun <V : Any> ofSingle(completionStage: CompletionStage<out V>): Deferred<V> {
            return DeferredSingleDesignFactory.ofSingleDesign<V>(KFuture.of(completionStage))
        }

        fun <V : Any> ofSingle(mono: Mono<out V>): Deferred<V> {
            return DeferredSingleDesignFactory.ofSingleDesign(mono)
        }
    }

    fun <R> map(mapper: (V) -> R): Deferred<R>

    fun <R> flatMapCompletionStage(mapper: (V) -> CompletionStage<out R>): Deferred<R>
}
