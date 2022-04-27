package funcify.feature.tools.container.deferred.template

import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.narrowed
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

internal interface ExecContextMonoDeferredTemplate :
    ExecutionContextDeferredTemplate<MonoDeferredContainerWT>, MonoDeferredTemplate {

    override fun <I, O> map(
        executor: Executor,
        mapper: (I) -> O,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(Schedulers.fromExecutor(executor)).map(mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        executor: Executor,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(Schedulers.fromExecutor(executor)).flatMap { i: I ->
                mapper.invoke(i).toMono()
            }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(Schedulers.fromExecutor(executor)).flatMap { i: I ->
                Mono.fromCompletionStage(mapper.invoke(i))
            }
        )
    }

    override fun <I, O> flatMapMono(
        executor: Executor,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(Schedulers.fromExecutor(executor)).flatMap(mapper)
        )
    }

    override fun <I, O> flatMapFlux(
        executor: Executor,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |Mono container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }

    override fun <I, O> map(
        scheduler: Scheduler,
        mapper: (I) -> O,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(scheduler).map(mapper)
        )
    }

    override fun <I, O> flatMapKFuture(
        scheduler: Scheduler,
        mapper: (I) -> KFuture<O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(scheduler).flatMap { i: I ->
                mapper.invoke(i).toMono()
            }
        )
    }

    override fun <I, O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(scheduler).flatMap { i: I ->
                Mono.fromCompletionStage(mapper.invoke(i))
            }
        )
    }

    override fun <I, O> flatMapMono(
        scheduler: Scheduler,
        mapper: (I) -> Mono<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        return DeferredContainerFactory.MonoDeferredContainer(
            container.narrowed().mono.publishOn(scheduler).flatMap(mapper)
        )
    }

    override fun <I, O> flatMapFlux(
        scheduler: Scheduler,
        mapper: (I) -> Flux<out O>,
        container: DeferredContainer<MonoDeferredContainerWT, I>
    ): DeferredContainer<MonoDeferredContainerWT, O> {
        throw UnsupportedOperationException(
            """use of this method [ name: flatMapFlux ] on a 
                |Mono container would potentially result in 
                |a loss of information and so is not supported""".flattenIntoOneLine()
        )
    }
}
