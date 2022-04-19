package funcify.feature.tools.container.deferred

import arrow.core.Option
import funcify.feature.tools.container.attempt.Try
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.stream.Stream

/**
 * @param SWT: Source Container Witness Type
 * @param I: Input Type for the Current Container Design
 */
internal interface DeferredDesign<SWT, I> : Deferred<I> {

    val template: DeferredTemplate<SWT>
    override fun <O> map(mapper: (I) -> O): Deferred<O> {
        return MapDesign<SWT, I, O>(template = template, currentDesign = this, mapper = mapper)
    }

    override fun <O> flatMapCompletionStage(mapper: (I) -> CompletionStage<out O>): Deferred<O> {
        return FlatMapCompletionStageDesign<SWT, I, O>(
            template = template,
            currentDesign = this,
            mapper = mapper
        )
    }

    override fun filter(condition: (I) -> Boolean): Deferred<I> {
        return FilterDesign<SWT, I>(
            template = template,
            currentDesign = this,
            condition = condition
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

    fun <WT> fold(template: DeferredTemplate<WT>): DeferredContainer<WT, I>
}
