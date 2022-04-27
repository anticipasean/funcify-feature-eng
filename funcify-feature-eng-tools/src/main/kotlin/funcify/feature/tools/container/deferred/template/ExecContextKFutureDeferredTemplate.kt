package funcify.feature.tools.container.deferred.template

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

internal interface ExecContextKFutureDeferredTemplate :
    ExecutionContextDeferredTemplate<KFutureDeferredContainerWT>, KFutureDeferredTemplate {
    override fun <I, O> map(
        executor: Executor,
        mapper: (I) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.map(executor, mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        executor: Executor,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMap(executor, mapper)
        )
    }

    override fun <I, O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMapCompletionStage(executor, mapper)
        )
    }

    override fun <I, O> flatMapMono(
        executor: Executor,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMapCompletionStage(executor) { i: I ->
                mapper.invoke(i).toFuture()
            }
        )
    }

    override fun <I, O> flatMapFlux(
        executor: Executor,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |KFuture container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I, O> map(
        scheduler: Scheduler,
        mapper: (I) -> O,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.map(Executor { r -> scheduler.schedule(r) }, mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        scheduler: Scheduler,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container
                .narrowed()
                .kFuture
                .flatMap(executor = Executor { r -> scheduler.schedule(r) }, mapper = mapper)
        )
    }

    override fun <I, O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container
                .narrowed()
                .kFuture
                .flatMapCompletionStage(Executor { r -> scheduler.schedule(r) }, mapper)
        )
    }

    override fun <I, O> flatMapMono(
        scheduler: Scheduler,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        return DeferredContainerFactory.KFutureDeferredContainer(
            container.narrowed().kFuture.flatMapCompletionStage(
                    Executor { r -> scheduler.schedule(r) }
                ) { i: I -> mapper.invoke(i).toFuture() }
        )
    }

    override fun <I, O> flatMapFlux(
        scheduler: Scheduler,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<KFutureDeferredContainerWT, I>
    ): DeferredContainer<KFutureDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |KFuture container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }
}
