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
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.KFutureDeferredContainer.Companion.KFutureDeferredContainerWT
import funcify.feature.tools.container.deferred.container.DeferredContainerFactory.MonoDeferredContainer.Companion.MonoDeferredContainerWT
import funcify.feature.tools.container.deferred.source.DeferredSourceContextFactory
import funcify.feature.tools.container.deferred.template.DeferredTemplate
import funcify.feature.tools.container.deferred.template.FluxDeferredTemplate
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.stream.Stream
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
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

    override fun <O> flatMapIterable(mapper: (I) -> Iterable<O>): Deferred<O> {
        return flatMapFlux { i: I -> Flux.fromIterable(mapper.invoke(i)) }
    }

    override fun <O> flatMapKFuture(mapper: (I) -> KFuture<O>): Deferred<O> {
        return FlatMapKFutureDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper
        )
    }

    override fun <O> flatMapKFuture(executor: Executor, mapper: (I) -> KFuture<O>): Deferred<O> {
        return FlatMapKFutureDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = executor.left().toOption()
        )
    }

    override fun <O> flatMapKFuture(scheduler: Scheduler, mapper: (I) -> KFuture<O>): Deferred<O> {
        return FlatMapKFutureDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper,
            execOpt = scheduler.right().toOption()
        )
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

    override fun flatMapFailure(mapper: (Throwable) -> Deferred<@UnsafeVariance I>): Deferred<I> {
        val fluxTransformer: (Flux<out I>) -> Flux<out I> = { inputFlux: Flux<out I> ->
            val wrappedMapper: (Throwable) -> Publisher<out Nothing> = { t: Throwable ->
                @Suppress("UNCHECKED_CAST") //
                mapper.invoke(t).toFlux() as Flux<out Nothing>
            }
            inputFlux.onErrorResume(wrappedMapper).map { i: I -> i }
        }
        return Deferred.fromFlux<I>(fluxTransformer.invoke(toFlux()))
    }

    override fun <I1, O> zip(other: Deferred<I1>, combiner: (I, I1) -> O): Deferred<O> {
        return Deferred.fromFlux(toFlux().zipWith(other.toFlux(), combiner))
    }

    override fun <I1, I2, O> zip2(
        other1: Deferred<I1>,
        other2: Deferred<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O> {
        return Deferred.fromFlux(
            toFlux()
                .zipWith(other1.toFlux()) { i: I, i1: I1 ->
                    { i2: I2 -> combiner.invoke(i, i1, i2) }
                }
                .zipWith<I2, O>(other2.toFlux()) { func: (I2) -> O, i2: I2 -> func.invoke(i2) }
        )
    }

    override fun <I1, I2, I3, O> zip3(
        other1: Deferred<I1>,
        other2: Deferred<I2>,
        other3: Deferred<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O> {
        return Deferred.fromFlux(
            toFlux()
                .zipWith<I1, (I2, I3) -> O>(other1.toFlux()) { i: I, i1: I1 ->
                    { i2: I2, i3: I3 -> combiner.invoke(i, i1, i2, i3) }
                }
                .zipWith<I2, (I3) -> O>(other2.toFlux()) { func: (I2, I3) -> O, i2: I2 ->
                    { i3: I3 -> func.invoke(i2, i3) }
                }
                .zipWith<I3, O>(other3.toFlux()) { func: (I3) -> O, i3: I3 -> func.invoke(i3) }
        )
    }

    override fun <I1, O> zip(other: KFuture<I1>, combiner: (I, I1) -> O): Deferred<O> {
        return ZipDesign<KFutureDeferredContainerWT, I, I1, Any, Any, O>(
            template = DeferredSourceContextFactory.kFutureTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, KFutureDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.kFutureTemplate
                ),
            other1 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other),
            combiner1 = combiner
        )
    }

    override fun <I1, I2, O> zip2(
        other1: KFuture<I1>,
        other2: KFuture<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O> {
        return ZipDesign<KFutureDeferredContainerWT, I, I1, I2, Any, O>(
            template = DeferredSourceContextFactory.kFutureTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, KFutureDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.kFutureTemplate
                ),
            other1 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other2),
            combiner2 = combiner
        )
    }

    override fun <I1, I2, I3, O> zip3(
        other1: KFuture<I1>,
        other2: KFuture<I2>,
        other3: KFuture<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O> {
        return ZipDesign<KFutureDeferredContainerWT, I, I1, I2, I3, O>(
            template = DeferredSourceContextFactory.kFutureTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, KFutureDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.kFutureTemplate
                ),
            other1 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other2),
            other3 = DeferredSourceContextFactory.KFutureDeferredSourceContext(other3),
            combiner3 = combiner
        )
    }

    override fun <I1, O> zip(other: Mono<I1>, combiner: (I, I1) -> O): Deferred<O> {
        return ZipDesign<MonoDeferredContainerWT, I, I1, Any, Any, O>(
            template = DeferredSourceContextFactory.monoTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, MonoDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.monoTemplate
                ),
            other1 = DeferredSourceContextFactory.MonoDeferredSourceContext(other),
            combiner1 = combiner
        )
    }

    override fun <I1, I2, O> zip2(
        other1: Mono<I1>,
        other2: Mono<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O> {
        return ZipDesign<MonoDeferredContainerWT, I, I1, I2, Any, O>(
            template = DeferredSourceContextFactory.monoTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, MonoDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.monoTemplate
                ),
            other1 = DeferredSourceContextFactory.MonoDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.MonoDeferredSourceContext(other2),
            combiner2 = combiner
        )
    }

    override fun <I1, I2, I3, O> zip3(
        other1: Mono<I1>,
        other2: Mono<I2>,
        other3: Mono<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O> {
        return ZipDesign<MonoDeferredContainerWT, I, I1, I2, I3, O>(
            template = DeferredSourceContextFactory.monoTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, MonoDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.monoTemplate
                ),
            other1 = DeferredSourceContextFactory.MonoDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.MonoDeferredSourceContext(other2),
            other3 = DeferredSourceContextFactory.MonoDeferredSourceContext(other3),
            combiner3 = combiner
        )
    }

    override fun <I1, O> zip(other: Flux<I1>, combiner: (I, I1) -> O): Deferred<O> {
        return ZipDesign<FluxDeferredContainerWT, I, I1, Any, Any, O>(
            template = DeferredSourceContextFactory.fluxTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.fluxTemplate
                ),
            other1 = DeferredSourceContextFactory.FluxDeferredSourceContext(other),
            combiner1 = combiner
        )
    }

    override fun <I1, I2, O> zip2(
        other1: Flux<I1>,
        other2: Flux<I2>,
        combiner: (I, I1, I2) -> O
    ): Deferred<O> {
        return ZipDesign<FluxDeferredContainerWT, I, I1, I2, Any, O>(
            template = DeferredSourceContextFactory.fluxTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.fluxTemplate
                ),
            other1 = DeferredSourceContextFactory.FluxDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.FluxDeferredSourceContext(other2),
            combiner2 = combiner
        )
    }

    override fun <I1, I2, I3, O> zip3(
        other1: Flux<I1>,
        other2: Flux<I2>,
        other3: Flux<I3>,
        combiner: (I, I1, I2, I3) -> O
    ): Deferred<O> {
        return ZipDesign<FluxDeferredContainerWT, I, I1, I2, I3, O>(
            template = DeferredSourceContextFactory.fluxTemplate,
            currentDesign =
                SwitchContainerTypeDesign<SWT, FluxDeferredContainerWT, I>(
                    sourceTemplate = template,
                    sourceDesign = this,
                    targetTemplate = DeferredSourceContextFactory.fluxTemplate
                ),
            other1 = DeferredSourceContextFactory.FluxDeferredSourceContext(other1),
            other2 = DeferredSourceContextFactory.FluxDeferredSourceContext(other2),
            other3 = DeferredSourceContextFactory.FluxDeferredSourceContext(other3),
            combiner3 = combiner
        )
    }

    override fun peek(ifSuccess: (I) -> Unit, ifFailure: (Throwable) -> Unit): Deferred<I> {
        return PeekDesign<SWT, I>(
            template = template,
            currentDesign = this,
            ifSuccessFunction = ifSuccess,
            ifFailureFunction = ifFailure
        )
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

    override fun toKFuture(): KFuture<List<I>> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.map { i: I -> listOf(i) }
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                KFuture.of(container.mono.mapNotNull { i: I -> listOf(i) }.toFuture())
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                KFuture.of(container.flux.collectList().toFuture())
            }
            else -> {
                throw UnsupportedOperationException(
                    "unhandled container type: [ ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun toMono(): Mono<out List<I>> {
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.map { i: I -> listOf(i) }.toMono()
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                container.mono.mapNotNull { i: I -> listOf(i) }
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                container.flux.collectList()
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

    override fun subscribe(s: Subscriber<in I>?) {
        if (s == null) {
            throw IllegalArgumentException("subscriber to ${Deferred::class.qualifiedName} is null")
        }
        return when (val container: DeferredContainer<SWT, I> = this.fold(template)) {
            is DeferredContainerFactory.KFutureDeferredContainer -> {
                container.kFuture.toMono().subscribe(s)
            }
            is DeferredContainerFactory.MonoDeferredContainer -> {
                container.mono.subscribe(s)
            }
            is DeferredContainerFactory.FluxDeferredContainer -> {
                container.flux.subscribe(s)
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
