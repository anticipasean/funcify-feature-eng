package funcify.feature.tools.container.deferred

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.DeferredContainerFactory.narrowed
import java.util.concurrent.CompletionStage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal interface KFutureDeferredTemplate : DeferredTemplate<KFutureDeferredContainerWT> {

    override fun <I> fromKFuture(
        kFuture: KFuture<I>
    ): DeferredContainer<KFutureDeferredContainerWT, I> {
        return DeferredContainerFactory.KFutureDeferredContainer(kFuture)
    }

    override fun <I> fromMono(mono: Mono<I>): DeferredContainer<KFutureDeferredContainerWT, I> {
        return fromKFuture(KFuture.of(mono.toFuture()))
    }

    /**
     * Should not be used unless caller is aware that only the first element of the flux will be
     * returned in the kFuture
     */
    override fun <I> fromFlux(flux: Flux<I>): DeferredContainer<KFutureDeferredContainerWT, I> {
        return fromKFuture(KFuture.of(flux.next().toFuture()))
    }

    override fun <I, O> map(
        mapper: (I) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.map(mapper)
        )
    }

    override fun <I, O> flatMapCompletionStage(
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMapCompletionStage(mapper)
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, I> {
        TODO("Not yet implemented")
    }
}
