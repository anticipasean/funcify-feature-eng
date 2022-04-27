package funcify.feature.tools.container.deferred.template

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
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
        throw UnsupportedOperationException(
            """use of this method [ name: fromFlux ] on a 
                |KFuture container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I, O> map(
        mapper: (I) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.map(mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMap(mapper)
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

    override fun <I, O> flatMapMono(
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMap { i: I -> KFuture.of(mapper.invoke(i).toFuture()) }
        )
    }

    /** Should not be called as there could be a loss of information */
    override fun <I, O> flatMapFlux(
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |KFuture container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        ifConditionUnmet: (I) -> Throwable,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, I> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.filter(condition, ifConditionUnmet)
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, Option<I>> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.filter(condition)
        )
    }
}
