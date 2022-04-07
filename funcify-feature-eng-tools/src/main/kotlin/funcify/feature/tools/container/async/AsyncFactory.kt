package funcify.feature.tools.container.async

import kotlinx.collections.immutable.PersistentList
import reactor.core.publisher.Flux
import java.util.concurrent.CompletionStage


/**
 *
 * @author smccarron
 * @created 2/8/22
 */
object AsyncFactory {

    sealed interface DeferredIterable<out V> {

        data class CompletionStageValue<V>(val valuesStage: CompletionStage<PersistentList<V>>) : DeferredIterable<V>

        data class FluxValue<V>(val valuesFlux: Flux<V>) : DeferredIterable<V>
    }

    data class AsyncCompletedSuccess<V>(val materializedValues: PersistentList<V>) : Async<V> {

        override fun <R> fold(succeededHandler: (PersistentList<V>) -> R,
                              erroredHandler: (Throwable) -> R,
                              deferredHandler: (DeferredIterable<V>) -> R): R {
            return succeededHandler.invoke(materializedValues)
        }

    }

    data class AsyncCompletedFailure<V>(val throwable: Throwable) : Async<V> {

        override fun <R> fold(succeededHandler: (PersistentList<V>) -> R,
                              erroredHandler: (Throwable) -> R,
                              deferredHandler: (DeferredIterable<V>) -> R): R {
            return erroredHandler.invoke(throwable)
        }

    }

    data class AsyncDeferredIterable<V>(val deferredIterable: DeferredIterable<V>) : Async<V> {

        override fun <R> fold(succeededHandler: (PersistentList<V>) -> R,
                              erroredHandler: (Throwable) -> R,
                              deferredHandler: (DeferredIterable<V>) -> R): R {
            return deferredHandler.invoke(deferredIterable)
        }

    }

}