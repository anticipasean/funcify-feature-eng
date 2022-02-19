package funcify.feature.tools.container.async

import arrow.core.*
import funcify.feature.tools.container.async.AsyncFactory.DeferredIterable
import funcify.feature.tools.container.async.AsyncFactory.DeferredIterable.CompletionStageValue
import funcify.feature.tools.container.async.AsyncFactory.DeferredIterable.FluxValue
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream


/**
 *
 * @author smccarron
 * @created 2/8/22
 */
interface Async<out V> : Iterable<V> {

    companion object {

        fun <V> succeeded(materializedValues: PersistentList<V>): Async<V> {
            return AsyncFactory.AsyncCompletedSuccess<V>(materializedValues)
        }

        fun <V> errored(throwable: Throwable): Async<V> {
            return AsyncFactory.AsyncCompletedFailure<V>(throwable)
        }

        fun <V> deferred(deferredIterable: DeferredIterable<V>): Async<V> {
            return AsyncFactory.AsyncDeferredIterable<V>(deferredIterable)
        }

        fun <V> succeededSingle(materializedValue: V): Async<V> {
            return succeeded(persistentListOf(materializedValue))
        }

        fun <V> empty(): Async<V> {
            return succeeded(persistentListOf())
        }

        fun <V> deferFromSupplier(executor: Executor, supplier: () -> V): Async<V> {
            return fromSingleValueCompletionStage(CompletableFuture.supplyAsync({ supplier.invoke() }, executor))
        }

        fun <V> deferFromStream(executor: Executor, supplier: () -> Stream<V>): Async<V> {
            return fromFlux(Flux.fromStream { supplier.invoke() }
                                    .subscribeOn(Schedulers.fromExecutor(executor)))
        }

        fun <V> deferFromIterable(executor: Executor, iterable: Iterable<V>): Async<V> {
            return fromFlux(Flux.fromIterable(iterable)
                                    .subscribeOn(Schedulers.fromExecutor(executor)))
        }

        fun <V> deferFromSequence(executor: Executor, sequence: Sequence<V>): Async<V> {
            return deferFromIterable(executor, sequence.asIterable())
        }

        fun <V> fromAttempt(attempt: Try<V>): Async<V> {
            return attempt.fold({ v: V ->
                                    succeededSingle(v)
                                }, { throwable: Throwable ->
                                    errored(throwable)
                                })
        }

        fun <V> fromStreamAttempt(attempt: Try<Stream<V>>): Async<V> {
            return attempt.fold({ v: Stream<V> ->
                                    succeeded(v.asSequence()
                                                      .toPersistentList())
                                }, { throwable: Throwable ->
                                    errored(throwable)
                                })
        }

        fun <V> fromStreamOfAttempts(attemptStream: Stream<Try<V>>): Async<V> {
            return fromFlux(Flux.fromStream(attemptStream)
                                    .flatMap { attempt -> attempt.fold({ v -> Flux.just(v) }, { thr -> Flux.error(thr) }) })
        }

        fun <V> fromSingleValueCompletionStage(completionStage: CompletionStage<V>): Async<V> {
            return when {
                completionStage.toCompletableFuture().isCompletedExceptionally -> {
                    Try.attemptNullable(completionStage.toCompletableFuture()::join)
                            .getFailure()
                            .map { e -> errored<V>(e) }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing exception despite completing exceptionally")) }
                }
                completionStage.toCompletableFuture().isDone -> {
                    Try.attempt(completionStage.toCompletableFuture()::join)
                            .getSuccess()
                            .map { v -> succeededSingle<V>(v) }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing successful result value despite not completing exceptionally")) }
                }
                else -> {
                    deferred(CompletionStageValue<V>(completionStage.thenApply { v -> persistentListOf<V>(v) }))
                }
            }
        }

        fun <V> fromCompletionStage(completionStage: CompletionStage<PersistentList<V>>): Async<V> {
            return when {
                completionStage.toCompletableFuture().isCompletedExceptionally -> {
                    Try.attemptNullable(completionStage.toCompletableFuture()::join)
                            .getFailure()
                            .map { e -> errored<V>(e) }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing exception despite completing exceptionally")) }
                }
                completionStage.toCompletableFuture().isDone -> {
                    Try.attempt(completionStage.toCompletableFuture()::join)
                            .getSuccess()
                            .map { vList ->
                                succeeded<V>(vList)
                            }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing successful result value despite not completing exceptionally")) }
                }
                else -> {
                    deferred(CompletionStageValue<V>(completionStage))
                }
            }
        }

        fun <V> fromNullableValuesStreamCompletionStage(completionStage: CompletionStage<PersistentList<V?>>): Async<V> {
            return when {
                completionStage.toCompletableFuture().isCompletedExceptionally -> {
                    Try.attemptNullable(completionStage.toCompletableFuture()::join)
                            .getFailure()
                            .map { e -> errored<V>(e) }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing exception despite completing exceptionally")) }
                }
                completionStage.toCompletableFuture().isDone -> {
                    Try.attempt(completionStage.toCompletableFuture()::join)
                            .getSuccess()
                            .map { vList ->
                                succeeded<V>(vList.flatMap { v ->
                                    v.toOption()
                                            .fold({ persistentListOf() }, { nonNullV -> persistentListOf(nonNullV) })
                                }
                                                     .toPersistentList())
                            }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage output is missing successful result value despite not completing exceptionally")) }
                }
                else -> {
                    deferred(CompletionStageValue<V>(completionStage.thenApply { vList ->
                        vList.flatMap { v ->
                            v.toOption()
                                    .fold({ persistentListOf() }, { nonNullV -> persistentListOf(nonNullV) })
                        }
                                .toPersistentList()
                    }))
                }
            }
        }

        fun <V> fromNullableSingleValueCompletionStage(completionStage: CompletionStage<V?>): Async<V> {
            return when {
                completionStage.toCompletableFuture().isCompletedExceptionally -> {
                    Try.attemptNullable(completionStage.toCompletableFuture()::get)
                            .getFailure()
                            .map { e -> errored<V>(e) }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage input is missing exception despite completing exceptionally")) }
                }
                completionStage.toCompletableFuture().isDone -> {
                    Try.attemptNullable(completionStage.toCompletableFuture()::get)
                            .getSuccess()
                            .map { vOpt ->
                                vOpt.fold({ empty() }) { v ->
                                    succeededSingle<V>(v)
                                }
                            }
                            .getOrElse { errored<V>(IllegalArgumentException("completion_stage input is missing successful result value despite completing unexceptionally")) }
                }
                else -> {
                    deferred(CompletionStageValue<V>(completionStage.thenCompose { v ->
                        v.toOption()
                                .fold({
                                          CompletableFuture.completedFuture(persistentListOf())
                                      }, { value ->
                                          CompletableFuture.completedFuture(persistentListOf(value))
                                      })
                    }))
                }
            }
        }

        fun <V> fromFlux(fluxValue: Flux<V>): Async<V> {
            return deferred(FluxValue<V>(fluxValue))
        }

        fun <V> fromMono(monoValue: Mono<V>): Async<V> {
            return deferred(FluxValue<V>(monoValue.flux()))
        }

        fun <V> fromNullableFluxValue(fluxValue: Flux<V?>): Async<V> {
            return deferred(FluxValue<V>(fluxValue.filter { v -> v != null }
                                                 .map { v -> v!! }))
        }

    }

    /**
     * Blocking method
     */
    override fun iterator(): Iterator<V> {
        return sequence().iterator()
    }

    /**
     * Blocking method
     */
    override fun spliterator(): Spliterator<@UnsafeVariance V> {
        return sequence().asStream()
                .spliterator()
    }

    /**
     * Blocking method
     */
    fun sequence(): Sequence<V> {
        return fold({ pList ->
                        pList.asSequence()
                    }, {
                        emptySequence()
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> {
                                Try.attempt(defVal.valuesStage.toCompletableFuture()::join)
                                        .map { stream -> stream.asSequence() }
                                        .fold({ seq -> seq }, { emptySequence() })
                                        .map { v -> v }
                            }
                            is FluxValue -> {
                                Try.attempt(defVal.valuesFlux::toStream)
                                        .sequence()
                                        .flatMap { stream -> stream.asSequence() }
                            }
                        }
                    })
    }


    fun <R> map(mapper: (V) -> R): Async<R> {
        return fold({ vList ->
                        succeeded(vList.asIterable()
                                          .map(mapper::invoke)
                                          .toPersistentList())
                    }, { throwable: Throwable ->
                        errored<R>(throwable)
                    }, { deferredValue: DeferredIterable<V> ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage<R>(deferredValue.valuesStage.thenApply { vList ->
                                    vList.map(mapper::invoke)
                                            .toPersistentList()
                                })
                            }
                            is FluxValue -> {
                                fromFlux<R>(deferredValue.valuesFlux.map(mapper::invoke))
                            }
                        }
                    })
    }

    fun <R> map(executor: Executor, mapper: (V) -> R): Async<R> {
        return fold({ vList ->
                        fromCompletionStage(CompletableFuture.supplyAsync({
                                                                              vList.map(mapper::invoke)
                                                                                      .toPersistentList()
                                                                          }, executor))
                    }, { throwable: Throwable ->
                        errored<R>(throwable)
                    }, { deferredValue: DeferredIterable<V> ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage<R>(deferredValue.valuesStage.thenApplyAsync({ vList ->
                                                                                                    vList.map(mapper::invoke)
                                                                                                            .toPersistentList()
                                                                                                }, executor))
                            }
                            is FluxValue -> {
                                fromFlux<R>(deferredValue.valuesFlux.publishOn(Schedulers.fromExecutor(executor))
                                                    .map(mapper::invoke))
                            }
                        }
                    })
    }

    fun <R> flatMap(mapper: (V) -> Async<R>): Async<R> {
        return fold({ vList ->
                        Try.fromOptional(vList.stream()
                                                 .map { v -> mapper.invoke(v) }
                                                 .map { asyncV ->
                                                     asyncV.toFlux()
                                                             .map { r -> r }
                                                 }
                                                 .reduce { f1: Flux<R>, f2: Flux<R> -> f1.concatWith(f2) })
                                .fold({ sFlux -> fromFlux(sFlux) }, { thr -> errored(thr) })
                    }, { throwable: Throwable ->
                        errored<R>(throwable)
                    }, { deferredValue: DeferredIterable<V> ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage<R>(deferredValue.valuesStage.thenCompose { vList ->
                                    vList.asSequence()
                                            .map(mapper::invoke)
                                            .map { asyncV -> asyncV.toCompletionStage() }
                                            .reduce { vListStage1, vListStage2 ->
                                                vListStage1.thenCombine(vListStage2) { vl1, vl2 ->
                                                    vl1.addAll(vl2)
                                                }
                                            }
                                            .thenApply { pList -> pList }
                                })
                            }
                            is FluxValue -> {
                                fromFlux<R>(deferredValue.valuesFlux.flatMap { v ->
                                    mapper.invoke(v)
                                            .toFlux()
                                            .map { r -> r }
                                })
                            }
                        }
                    })
    }

    fun <R> flatMap(executor: Executor, mapper: (V) -> Async<R>): Async<R> {
        return fold({ vList ->
                        fromFlux(Flux.fromStream(vList.stream())
                                         .publishOn(Schedulers.fromExecutor(executor))
                                         .map { v -> mapper.invoke(v) }
                                         .flatMap { asyncV ->
                                             asyncV.toFlux()
                                         })
                    }, { throwable: Throwable ->
                        errored<R>(throwable)
                    }, { deferredValue: DeferredIterable<V> ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage<R>(deferredValue.valuesStage.thenComposeAsync({ vList ->
                                                                                                      vList.asSequence()
                                                                                                              .map(mapper::invoke)
                                                                                                              .map { asyncV -> asyncV.toCompletionStage() }
                                                                                                              .reduce { vList1, vList2 ->
                                                                                                                  vList1.thenCombine(vList2) { vl1, vl2 ->
                                                                                                                      vl1.addAll(vl2)
                                                                                                                  }
                                                                                                              }
                                                                                                              .thenApply { pList -> pList }
                                                                                                  }, executor))
                            }
                            is FluxValue -> {
                                fromFlux<R>(deferredValue.valuesFlux.flatMap { v ->
                                    mapper.invoke(v)
                                            .toFlux()
                                            .map { r -> r }
                                })
                            }
                        }
                    })
    }

    fun filter(condition: (V) -> Boolean): Async<V> {
        return fold({ vList ->
                        succeeded(vList.filter(condition::invoke)
                                          .toPersistentList())
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage(deferredValue.valuesStage.thenApply { vList ->
                                    vList.filter(condition::invoke)
                                            .toPersistentList()
                                })
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.filter(condition::invoke))
                            }
                        }
                    })
    }

    fun filterToOption(condition: (V) -> Boolean): Async<Option<V>> {
        return fold({ vList ->
                        succeeded(vList.asSequence()
                                          .map { v ->
                                              v.some()
                                                      .filter(condition::invoke)
                                          }
                                          .toPersistentList())
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage(deferredValue.valuesStage.thenApply { vList ->
                                    vList.map { v ->
                                        v.some()
                                                .filter(condition::invoke)
                                    }
                                            .toPersistentList()
                                })
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.map { v ->
                                    v.some()
                                            .filter(condition::invoke)
                                })
                            }
                        }
                    })
    }

    fun filter(condition: (V) -> Boolean, ifConditionNotMet: (V) -> Throwable): Async<V> {
        return fold({ vList ->
                        fromFlux(Flux.fromStream(vList::stream)
                                         .flatMap { v ->
                                             Try.success(v)
                                                     .filter(condition::invoke, ifConditionNotMet::invoke)
                                                     .fold({ s -> Flux.just(s) }, { thr -> Flux.error(thr) })
                                         })
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromCompletionStage(deferredValue.valuesStage.thenApply { vList ->
                                    vList.map { v ->
                                        Try.success(v)
                                                .filter(condition::invoke, ifConditionNotMet::invoke)
                                                .orElseThrow()
                                    }
                                            .toPersistentList()
                                })
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.flatMap { v ->
                                    Try.success(v)
                                            .filter(condition::invoke, ifConditionNotMet::invoke)
                                            .fold({ s -> Flux.just(s) }, { thr -> Flux.error(thr) })
                                })
                            }
                        }
                    })
    }

    fun <A, R> zip(other: Async<A>, combiner: (V, A) -> R): Async<R> {
        return fold({ vList ->
                        other.fold({ aStream ->
                                       fromStreamAttempt(Try.attempt({
                                                                         vList.asSequence()
                                                                                 .zip(aStream.asSequence(), combiner::invoke)
                                                                     })
                                                                 .map { seq ->
                                                                     seq.asStream()
                                                                 })
                                   }, { thr ->
                                       errored(thr)
                                   }, { defVal ->
                                       when (defVal) {
                                           is CompletionStageValue -> {
                                               fromCompletionStage(defVal.valuesStage.thenCombine(CompletableFuture.completedFuture(vList)) { aValStream, vValStream ->
                                                   vValStream.asSequence()
                                                           .zip(aValStream.asSequence(), combiner::invoke)
                                                           .toPersistentList()
                                               })
                                           }
                                           is FluxValue -> {
                                               fromFlux(Flux.fromStream(vList::stream)
                                                                .zipWith(defVal.valuesFlux, combiner::invoke))
                                           }
                                       }
                                   })
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                if (!(other is AsyncFactory.AsyncDeferredIterable<*> && other.deferredIterable is FluxValue<*>)) {
                                    fromCompletionStage(deferredValue.valuesStage.thenCombine(other.toCompletionStage()) { vVal, aVal ->
                                        vVal.asSequence()
                                                .zip(aVal.asSequence(), combiner::invoke)
                                                .toPersistentList()
                                    })
                                } else {
                                    fromFlux(Mono.fromCompletionStage(deferredValue.valuesStage)
                                                     .flatMapMany { vList -> Flux.fromIterable(vList) }
                                                     .zipWith(other.toFlux(), combiner::invoke))
                                }
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.zipWith(other.toFlux()) { vVal, aVal -> combiner.invoke(vVal, aVal) })
                            }
                        }
                    })
    }

    fun <A, R> zip(executor: Executor, other: Async<A>, combiner: (V, A) -> R): Async<R> {
        return fold({ vList ->
                        other.fold({ aList ->
                                       fromFlux(Flux.fromStream(vList::stream)
                                                        .publishOn(Schedulers.fromExecutor(executor))
                                                        .zipWith(Flux.fromStream(aList::stream), combiner::invoke))
                                   }, { thr ->
                                       errored(thr)
                                   }, { defVal ->
                                       when (defVal) {
                                           is CompletionStageValue -> {
                                               fromCompletionStage(defVal.valuesStage.thenCombineAsync(CompletableFuture.completedFuture(vList),
                                                                                                       { aValStream, vValStream ->
                                                                                                           vValStream.asSequence()
                                                                                                                   .zip(aValStream.asSequence(),
                                                                                                                        combiner::invoke)
                                                                                                                   .toPersistentList()
                                                                                                       },
                                                                                                       executor))
                                           }
                                           is FluxValue -> {
                                               fromFlux(Flux.fromStream(vList::stream)
                                                                .publishOn(Schedulers.fromExecutor(executor))
                                                                .zipWith(defVal.valuesFlux, combiner::invoke))
                                           }
                                       }
                                   })
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                if (!(other is AsyncFactory.AsyncDeferredIterable<*> && other.deferredIterable is FluxValue<*>)) {
                                    fromCompletionStage(deferredValue.valuesStage.thenCombineAsync(other.toCompletionStage(), { vVal, aVal ->
                                        vVal.asSequence()
                                                .zip(aVal.asSequence(), combiner::invoke)
                                                .toPersistentList()
                                    }, executor))
                                } else {
                                    fromFlux(Mono.fromCompletionStage(deferredValue.valuesStage)
                                                     .publishOn(Schedulers.fromExecutor(executor))
                                                     .flatMapMany { vList -> Flux.fromIterable(vList) }
                                                     .zipWith(other.toFlux(), combiner::invoke))
                                }
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.publishOn(Schedulers.fromExecutor(executor))
                                                 .zipWith(other.toFlux(), combiner::invoke))
                            }
                        }
                    })
    }

    fun blockFirst(): Try<V> {
        return fold({ vList ->
                        Try.fromOption(vList.firstOrNone())
                    }, { thr ->
                        Try.failure(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> Try.attempt(defVal.valuesStage.toCompletableFuture()::join)
                                    .map { vList -> vList.firstOrNone() }
                                    .flatMap { firstOpt -> Try.fromOption(firstOpt) }
                            is FluxValue -> Try.attemptNullable(defVal.valuesFlux::blockFirst) {
                                IllegalArgumentException("no values were returned for flux subscriptions (or none that is non-null)")
                            }
                        }
                    })
    }

    fun blockFirstOption(): Option<V> {
        return blockFirst().getSuccess()
    }

    fun blockFirstOrElseGet(defaultSupplier: () -> @UnsafeVariance V): V {
        return blockFirst().getSuccess()
                .getOrElse(defaultSupplier)
    }

    fun blockFirstOrElseThrow(): V {
        return blockFirst().orElseThrow()
    }

    fun block(): Try<PersistentList<V>> {
        return fold({ vList ->
                        Try.success(vList)
                    }, { thr ->
                        Try.failure(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> Try.attempt(defVal.valuesStage.toCompletableFuture()::join)
                            is FluxValue -> Try.attemptNullable(defVal.valuesFlux::toStream) {
                                IllegalArgumentException("no stream of values was returned for flux subscriptions (or none that is non-null)")
                            }
                                    .map { vStream ->
                                        vStream.asSequence()
                                                .toPersistentList()
                                    }
                        }
                    })
    }

    fun blockOrElseThrow(): PersistentList<V> {
        return block().orElseThrow()
    }

    fun blockOrElseGet(defaultSupplier: () -> PersistentList<@UnsafeVariance V>): PersistentList<V> {
        return block().orElseGet(defaultSupplier)
    }

    fun blockLast(): Try<V> {
        return fold({ vList ->
                        Try.attempt {
                            if (vList.size >= 1) {
                                vList[vList.size - 1].toOption()
                            } else {
                                None
                            }
                        }
                                .flatMap { vOpt -> Try.fromOption(vOpt) }
                    }, { thr ->
                        Try.failure(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> {
                                Try.attempt(defVal.valuesStage.toCompletableFuture()::join)
                                        .map { vList ->
                                            if (vList.size >= 1) {
                                                vList[vList.size - 1].toOption()
                                            } else {
                                                None
                                            }
                                        }
                                        .flatMap { vOpt -> Try.fromOption(vOpt) }
                            }
                            is FluxValue -> {
                                Try.attemptNullable(defVal.valuesFlux::blockLast) { NoSuchElementException("no elements within flux value") }
                            }
                        }
                    })
    }

    fun blockLastOption(): Option<V> {
        return blockLast().getSuccess()
    }

    fun blockLastOrElseGet(defaultSupplier: () -> @UnsafeVariance V): V {
        return blockLast().orElseGet(defaultSupplier)
    }

    fun blockLastOrElseThrow(): V {
        return blockLast().orElseThrow()
    }

    fun <R> reduce(initial: R, accumulator: (R, V) -> R, combiner: (R, R) -> R = { _, r2 -> r2 }): Async<R> {
        return fold({ vList ->
                        succeededSingle(vList.stream()
                                                .reduce(initial, accumulator::invoke, combiner::invoke))
                    }, { thr ->
                        errored(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> {
                                fromSingleValueCompletionStage(defVal.valuesStage.thenApply { vList ->
                                    vList.stream()
                                            .reduce(initial, accumulator::invoke, combiner::invoke)
                                })
                            }
                            is FluxValue -> {
                                fromFlux(defVal.valuesFlux.reduce(initial, accumulator::invoke)
                                                 .flux())
                            }
                        }
                    })
    }

    fun <R> reduce(executor: Executor, initial: R, accumulator: (R, V) -> R, combiner: (R, R) -> R = { _, r2 -> r2 }): Async<R> {
        return fold({ vList ->
                        fromSingleValueCompletionStage(CompletableFuture.supplyAsync({
                                                                                         vList.stream()
                                                                                                 .reduce(initial,
                                                                                                         accumulator::invoke,
                                                                                                         combiner::invoke)
                                                                                     }, executor))
                    }, { thr ->
                        errored(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> {
                                fromSingleValueCompletionStage(defVal.valuesStage.thenApplyAsync({ vList ->
                                                                                                     vList.stream()
                                                                                                             .reduce(initial,
                                                                                                                     accumulator::invoke,
                                                                                                                     combiner::invoke)
                                                                                                 }, executor))
                            }
                            is FluxValue -> {
                                fromFlux(defVal.valuesFlux.publishOn(Schedulers.fromExecutor(executor))
                                                 .reduce(initial, accumulator::invoke)
                                                 .flux())
                            }
                        }
                    })
    }

    /**
     * In parallel reduction, a combiner function, one handling the combining of any two leaf nodes of any given set of reduction trees,
     * must be provided to ensure values are not lost in the result:
     * `parallelReduce(executor, persistentListOf<String>(), { pList, nextStr -> pList.add(nextStr) }, { pList1, pList2 -> pList1.addAll(pList2) })`
     * When reduction is not done in parallel, there is only one leaf node, so the combiner function can be defaulted to `{ result1, result2 -> result2 }`
     */
    fun <R> parallelReduce(executor: Executor, initial: R, accumulator: (R, V) -> R, combiner: (R, R) -> R): Async<R> {
        return fold({ vList ->
                        fromSingleValueCompletionStage(CompletableFuture.supplyAsync({
                                                                                         vList.stream()
                                                                                                 .parallel()
                                                                                                 .reduce(initial,
                                                                                                         accumulator::invoke,
                                                                                                         combiner::invoke)
                                                                                     }, executor))
                    }, { thr ->
                        errored(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> {
                                fromSingleValueCompletionStage(defVal.valuesStage.thenApplyAsync({ vList ->
                                                                                                     vList.stream()
                                                                                                             .parallel()
                                                                                                             .reduce(initial,
                                                                                                                     accumulator::invoke,
                                                                                                                     combiner::invoke)
                                                                                                 }, executor))
                            }
                            is FluxValue -> {
                                fromFlux(defVal.valuesFlux.publishOn(Schedulers.fromExecutor(executor))
                                                 .parallel()
                                                 .reduce({ initial }, accumulator::invoke)
                                                 .sequential())
                            }
                        }
                    })
    }

    fun partition(condition: (V) -> Boolean): Async<Pair<Sequence<V>, Sequence<V>>> {
        return fold({ vList ->
                        vList.map({ v ->
                                      v.some()
                                              .filter(condition)
                                              .fold({ v.right() }, { v.left() })
                                  })
                                .asSequence()
                                .separateEither()
                                .let { pair ->
                                    succeededSingle(Pair(pair.first.map { v -> v }, pair.second.map { v -> v }))
                                }
                    }, { thr ->
                        errored(thr)
                    }, { deferredValue ->
                        when (deferredValue) {
                            is CompletionStageValue -> {
                                fromSingleValueCompletionStage(deferredValue.valuesStage.thenApply { vStream ->
                                    vStream.map({ v ->
                                                    v.some()
                                                            .filter(condition)
                                                            .fold({ v.right() }, { v.left() })
                                                })
                                            .asSequence()
                                            .separateEither()
                                            .let { pair ->
                                                Pair(pair.first.map { v -> v }, pair.second.map { v -> v })
                                            }
                                })
                            }
                            is FluxValue -> {
                                fromFlux(deferredValue.valuesFlux.map({ v ->
                                                                          v.some()
                                                                                  .filter(condition)
                                                                                  .fold({ v.right() }, { v.left() })

                                                                      })
                                                 .collect({ Pair(Stream.builder(), Stream.builder()) },
                                                          { pairStreamBuilders: Pair<Stream.Builder<V>, Stream.Builder<V>>, metUnmetEither: Either<V, V> ->
                                                              metUnmetEither.fold({ lv -> pairStreamBuilders.first.add(lv) },
                                                                                  { rv -> pairStreamBuilders.second.add(rv) })
                                                          })
                                                 .map({ pairStreamBuilders ->
                                                          Pair(pairStreamBuilders.first.build()
                                                                       .asSequence(),
                                                               pairStreamBuilders.second.build()
                                                                       .asSequence())
                                                      })
                                                 .flux())
                            }
                        }
                    })
    }

    fun toFlux(): Flux<out V> {
        return fold({ vList ->
                        Flux.fromStream(vList::stream)
                    }, { thr ->
                        Flux.error(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> Mono.fromCompletionStage(defVal.valuesStage)
                                    .flatMapMany { vList -> Flux.fromIterable(vList) }
                            is FluxValue -> defVal.valuesFlux
                        }
                    })
    }

    fun toCompletionStage(): CompletionStage<out PersistentList<V>> {
        return fold({ vList ->
                        CompletableFuture.completedStage(vList)
                    }, { thr ->
                        CompletableFuture.failedStage(thr)
                    }, { defVal ->
                        when (defVal) {
                            is CompletionStageValue -> defVal.valuesStage
                            /**
                             * use of Flux#toStream would be a blocking operation so Flux#collectList
                             * is preferred instead
                             */
                            is FluxValue -> defVal.valuesFlux.collectList()
                                    .toFuture()
                                    .thenApply { vList -> vList.toPersistentList() }
                        }
                    })
    }

    fun <R> fold(succeededHandler: (PersistentList<V>) -> R, erroredHandler: (Throwable) -> R, deferredHandler: (DeferredIterable<V>) -> R): R


}