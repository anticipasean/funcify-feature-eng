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
     * Should only be called when zipping with single value containers since at most one value will
     * come from the containers with which this is being zipped
     */
    override fun <I> fromFlux(flux: Flux<I>): DeferredContainer<KFutureDeferredContainerWT, I> {
        return DeferredContainerFactory.KFutureDeferredContainer(KFuture.of(flux.next().toFuture()))
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

    /**
     * Should not be called as there could be a loss of information: (single) to (many).only_first
     */
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

    override fun <I, I1, O> zip1(
        other: DeferredContainer<KFutureDeferredContainerWT, I1>,
        combiner: (I, I1) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.zip(other.narrowed().kFuture, combiner)
        )
    }

    override fun <I, I1, I2, O> zip2(
        other1: DeferredContainer<KFutureDeferredContainerWT, I1>,
        other2: DeferredContainer<KFutureDeferredContainerWT, I2>,
        combiner: (I, I1, I2) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container
                .narrowed()
                .kFuture
                .zip2(other1.narrowed().kFuture, other2.narrowed().kFuture, combiner)
        )
    }

    override fun <I, I1, I2, I3, O> zip3(
        other1: DeferredContainer<KFutureDeferredContainerWT, I1>,
        other2: DeferredContainer<KFutureDeferredContainerWT, I2>,
        other3: DeferredContainer<KFutureDeferredContainerWT, I3>,
        combiner: (I, I1, I2, I3) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container
                .narrowed()
                .kFuture
                .zip3(
                    other1.narrowed().kFuture,
                    other2.narrowed().kFuture,
                    other3.narrowed().kFuture,
                    combiner
                )
        )
    }

    override fun <I> peek(
        ifSuccess: (I) -> Unit,
        ifFailure: (Throwable) -> Unit,
        container: DeferredContainer<KFutureDeferredContainerWT, I>,
    ): DeferredContainer<KFutureDeferredContainerWT, I> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.peek(ifSuccess, ifFailure)
        )
    }
}
