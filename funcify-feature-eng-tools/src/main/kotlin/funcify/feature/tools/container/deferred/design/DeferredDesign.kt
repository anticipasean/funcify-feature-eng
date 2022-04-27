package funcify.feature.tools.container.deferred.design

import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.deferred.container.DeferredContainer
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.FluxDeferredContainer.Companion.FluxDeferredContainerWT
import funcify.feature.tools.container.deferred.source.DeferredSourceContextFactory
import funcify.feature.tools.container.deferred.template.DeferredTemplate
import funcify.feature.tools.container.deferred.template.FluxDeferredTemplate
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.stream.Stream
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

/**
 * @param SWT: Source Container Witness Type
 * @param I: Input Type for the Current Container Design
 */
internal interface DeferredDesign<SWT, I> : Deferred<I> {

    val template: DeferredTemplate<SWT>
    override fun <O> map(mapper: (I) -> O): Deferred<O> {
        return MapDesign<SWT, I, O>(template = template, currentDesign = this, mapper = mapper)
    }

    override fun <O> flatMap(mapper: (I) -> Deferred<O>): Deferred<O> {
        val updatedMapper: (I) -> Flux<out O> = { i: I -> mapper.invoke(i).toFlux() }
        return flatMapFlux(mapper = updatedMapper)
    }

    override fun <O> flatMap(executor: Executor, mapper: (I) -> Deferred<O>): Deferred<O> {
        val updatedMapper: (I) -> Flux<out O> = { i: I -> mapper.invoke(i).toFlux() }
        return flatMapFlux(executor = executor, mapper = updatedMapper)
    }

    override fun <O> flatMap(scheduler: Scheduler, mapper: (I) -> Deferred<O>): Deferred<O> {
        val updatedMapper: (I) -> Flux<out O> = { i: I -> mapper.invoke(i).toFlux() }
        return flatMapFlux(scheduler = scheduler, mapper = updatedMapper)
    }

    override fun <O> flatMapCompletionStage(mapper: (I) -> CompletionStage<out O>): Deferred<O> {
        return FlatMapCompletionStageDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper
        )
    }

    override fun <O> flatMapMono(mapper: (I) -> Mono<out O>): Deferred<O> {
        return FlatMapMonoDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper
        )
    }

    override fun <O> flatMapFlux(mapper: (I) -> Flux<out O>): Deferred<O> {
        return if (template is FluxDeferredTemplate) {
            FlatMapFluxDesign<SWT, I, O>(template = template, currentDesign = this, mapper = mapper)
        } else {
            FlatMapFluxDesign<FluxDeferredContainerWT, I, O>(
                template = DeferredSourceContextFactory.fluxTemplate,
                currentDesign =
                    SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                        sourceDesign = this,
                        sourceTemplate = template,
                        targetTemplate = DeferredSourceContextFactory.fluxTemplate
                    ),
                mapper = mapper
            )
        }
    }

    override fun filter(condition: (I) -> Boolean): Deferred<Option<I>> {
        return FilterOptionDesign<SWT, I>(
            template = template,
            currentDesign = this,
            condition = condition
        )
    }

    override fun filter(
        condition: (I) -> Boolean,
        ifConditionNotMet: (I) -> Throwable
    ): Deferred<I> {
        return FilterDesign<SWT, I>(
            template = template,
            currentDesign = this,
            condition = condition,
            ifConditionUnmet = ifConditionNotMet
        )
    }

    override fun <O> map(executor: Executor, mapper: (I) -> O): Deferred<O> {
        return MapDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = executor.left().toOption()
        )
    }

    override fun <O> map(scheduler: Scheduler, mapper: (I) -> O): Deferred<O> {
        return MapDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = scheduler.right().toOption()
        )
    }

    override fun <O> flatMapCompletionStage(
        executor: Executor,
        mapper: (I) -> CompletionStage<out O>
    ): Deferred<O> {
        return FlatMapCompletionStageDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = executor.left().toOption()
        )
    }

    override fun <O> flatMapCompletionStage(
        scheduler: Scheduler,
        mapper: (I) -> CompletionStage<out O>
    ): Deferred<O> {
        return FlatMapCompletionStageDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = scheduler.right().toOption()
        )
    }

    override fun <O> flatMapMono(executor: Executor, mapper: (I) -> Mono<out O>): Deferred<O> {
        return FlatMapMonoDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = executor.left().toOption()
        )
    }

    override fun <O> flatMapMono(scheduler: Scheduler, mapper: (I) -> Mono<out O>): Deferred<O> {
        return FlatMapMonoDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = scheduler.right().toOption()
        )
    }

    override fun <O> flatMapFlux(executor: Executor, mapper: (I) -> Flux<out O>): Deferred<O> {
        return if (template is FluxDeferredTemplate) {
            FlatMapFluxDesign<SWT, I, O>(
                template = template,
                currentDesign = this,
                mapper = mapper,
                execOpt = executor.left().toOption()
            )
        } else {
            FlatMapFluxDesign<FluxDeferredContainerWT, I, O>(
                template = DeferredSourceContextFactory.fluxTemplate,
                currentDesign =
                    SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                        sourceDesign = this,
                        sourceTemplate = template,
                        targetTemplate = DeferredSourceContextFactory.fluxTemplate
                    ),
                mapper = mapper,
                execOpt = executor.left().toOption()
            )
        }
    }

    override fun <O> flatMapFlux(scheduler: Scheduler, mapper: (I) -> Flux<out O>): Deferred<O> {
        return if (template is FluxDeferredTemplate) {
            FlatMapFluxDesign<SWT, I, O>(
                template = template,
                currentDesign = this,
                mapper = mapper,
                execOpt = scheduler.right().toOption()
            )
        } else {
            FlatMapFluxDesign<FluxDeferredContainerWT, I, O>(
                template = DeferredSourceContextFactory.fluxTemplate,
                currentDesign =
                    SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                        sourceDesign = this,
                        sourceTemplate = template,
                        targetTemplate = DeferredSourceContextFactory.fluxTemplate
                    ),
                mapper = mapper,
                execOpt = scheduler.right().toOption()
            )
        }
    }

    override fun iterator(): Iterator<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get().iterator()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable { container.mono.block() }
                    .flatMap(Try.Companion::fromOption)
                    .iterator()
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable { container.flux.collectList().block() }
                    .fold(
                        { lOpt: Option<List<I>> -> lOpt.fold({ listOf() }, { l -> l }) },
                        { listOf() }
                    )
                    .iterator()
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun batchedIterator(batchSize: Int): Iterator<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get().iterator()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable { container.mono.block() }
                    .flatMap(Try.Companion::fromOption)
                    .iterator()
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable { container.flux.toIterable(batchSize) }
                    .flatMap(Try.Companion::fromOption)
                    .map(Iterable<I>::iterator)
                    .orElse(Collections.emptyIterator<I>())
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun spliterator(): Spliterator<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get().spliterator()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable { container.mono.block() }
                    .fold({ iOpt: Option<I> -> iOpt.toList() }, { listOf() })
                    .spliterator()
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable { container.flux.collectList().block() }
                    .fold(
                        { lOpt: Option<List<I>> -> lOpt.fold({ listOf() }, { l -> l }) },
                        { listOf() }
                    )
                    .spliterator()
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun stream(bufferSize: Int): Stream<out I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get().stream()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable { container.mono.block() }
                    .flatMap(Try.Companion::fromOption)
                    .stream()
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable { container.flux.toStream(bufferSize) }
                    .flatMap(Try.Companion::fromOption)
                    .fold({ s -> s }, { Stream.empty() })
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }
    override fun blockForFirst(): Try<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable({ container.mono.block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Mono::class.qualifiedName}"
                    )
                }
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable({ container.flux.next().block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Flux::class.qualifiedName}"
                    )
                }
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun blockForLast(): Try<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable({ container.mono.block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Mono::class.qualifiedName}"
                    )
                }
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable({ container.flux.last().block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Flux::class.qualifiedName}"
                    )
                }
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun blockForAll(): Try<List<I>> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.get().map { i: I -> listOf(i) }
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                Try.attemptNullable({ container.mono.block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Mono::class.qualifiedName}"
                    )
                }
                    .map { i: I -> listOf(i) }
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                Try.attemptNullable({ container.flux.collectList().block() }) {
                    NoSuchElementException(
                        "no non-null output was received from ${Flux::class.qualifiedName}"
                    )
                }
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun toKFuture(): KFuture<I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                KFuture.of(container.mono.toFuture())
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                KFuture.of(container.flux.next().toFuture())
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun toMono(): Mono<out I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.toMono()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                container.mono
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                container.flux.next()
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun toFlux(): Flux<out I> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.toMono().flux()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                container.mono.flux()
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                container.flux
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I>
}
