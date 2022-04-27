package funcify.feature.tools.container.deferred.template

import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletionStage

internal interface MonoDeferredTemplate : DeferredTemplate<MonoDeferredContainerWT> {
    override fun <I> fromKFuture(
        kFuture: KFuture<I>
    ): DeferredContainer<MonoDeferredContainerWT, I> {
        return DeferredContainerFactory.MonoDeferredContainer(kFuture.toMono())
    }

    override fun <I> fromMono(mono: Mono<I>): DeferredContainer<MonoDeferredContainerWT, I> {
        return DeferredContainerFactory.MonoDeferredContainer(mono)
    }

    /** avoid use since information could be lost */
    override fun <I> fromFlux(flux: Flux<I>): DeferredContainer<MonoDeferredContainerWT, I> {
        throw UnsupportedOperationException(
            """use of this method [ name: fromFlux ] on a 
                |Mono container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I, O> map(
        mapper: (I) -> O,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(container.narrowed().mono.map(mapper))
    }

    override fun <I, O> flatMapCompletionStage(
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.flatMap { i: I -> Mono.fromCompletionStage(mapper.invoke(i)) }
                                                             )
    }

    override fun <I, O> flatMapMono(
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.flatMap(mapper)
                                                             )
    }

    /** Should not be called since it could mean a loss of information */
    override fun <I, O> flatMapFlux(
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |Mono container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        ifConditionUnmet: (I) -> Throwable,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, I> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.flatMap { i: I ->
                if (condition.invoke(i)) {
                    Mono.just(i)
                } else {
                    Mono.error<I> { ifConditionUnmet.invoke(i) }
                }
            }
                                                             )
    }

    override fun <I> filter(
        condition: (I) -> Boolean,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, Option<I>> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.map { i: I ->
                if (condition.invoke(i)) {
                    i.toOption()
                } else {
                    None
                }
            }
                                                             )
    }
}
