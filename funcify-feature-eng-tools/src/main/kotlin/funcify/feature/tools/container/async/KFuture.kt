package funcify.feature.tools.container.async

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import funcify.feature.tools.container.async.KFutureFactory.WrappedCompletionStageAndExecutor
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.PredicateExtensions.negate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 4/6/22
 */
interface KFuture<out T> : Publisher<@UnsafeVariance T> {

    companion object {

        @JvmStatic
        fun <T> of(completionStage: CompletionStage<T>): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(completionStage = completionStage)
        }

        @JvmStatic
        fun <T> of(completionStage: CompletionStage<T>, executor: Executor): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(
                completionStage = completionStage,
                executorOpt = executor.toOption()
            )
        }

        @JvmStatic
        fun <T> defer(function: () -> T): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(
                completionStage = CompletableFuture.supplyAsync(function)
            )
        }

        @JvmStatic
        fun <T> defer(executor: Executor, function: () -> T): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(
                completionStage = CompletableFuture.supplyAsync(function, executor),
                executorOpt = executor.toOption()
            )
        }

        @JvmStatic
        @JvmOverloads
        fun <T> completed(value: T, executor: Executor? = null): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(
                completionStage = CompletableFuture.completedFuture(value),
                executorOpt = executor.toOption()
            )
        }

        @JvmStatic
        @JvmOverloads
        fun <T> emptyCompleted(executor: Executor? = null): KFuture<Option<T>> {
            return WrappedCompletionStageAndExecutor<Option<T>>(
                completionStage = CompletableFuture.completedFuture(none<T>()),
                executorOpt = executor.toOption()
            )
        }

        @JvmStatic
        @JvmOverloads
        fun <T> failed(throwable: Throwable, executor: Executor? = null): KFuture<T> {
            return WrappedCompletionStageAndExecutor<T>(
                completionStage = CompletableFuture.failedFuture(throwable),
                executorOpt = executor.toOption()
            )
        }

        @JvmStatic
        fun <T> fromAttempt(attempt: Try<T>): KFuture<T> {
            return attempt.fold({ t: T -> completed(t) }, { t: Throwable -> failed(t) })
        }

        @JvmStatic
        fun <T> fromMono(mono: Mono<T>): KFuture<T> {
            return of(mono.toFuture())
        }

        @JvmStatic
        fun <T> combineArrayOf(vararg futures: KFuture<T>): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futures.asSequence().fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList))
        }

        @JvmStatic
        fun <T> combineArrayOf(
            executor: Executor,
            vararg futures: KFuture<T>
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futures.asSequence().fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList, executor), executor)
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, I : Iterable<KF>> combineIterableOf(
            futures: I
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futures.asSequence().fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList))
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, I : Iterable<KF>> combineIterableOf(
            executor: Executor,
            futures: I
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futures.asSequence().fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList, executor), executor)
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, S : Sequence<KF>> combineSequenceOf(
            futuresSequence: S
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futuresSequence.fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }

                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList))
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, S : Sequence<KF>> combineSequenceOf(
            executor: Executor,
            futuresSequence: S
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futuresSequence.fold(persistentListOf()) { pl, kf ->
                        kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) }
                    }
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList, executor), executor)
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, S : Stream<KF>> combineStreamOf(
            futuresStream: S
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futuresStream.reduce(
                        persistentListOf(),
                        { pl, kf -> kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) } },
                        { pl1, pl2 -> pl1.addAll(pl2) }
                    )
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList))
        }

        @JvmStatic
        fun <T, KF : KFuture<T>, S : Stream<KF>> combineStreamOf(
            executor: Executor,
            futuresStream: S
        ): KFuture<ImmutableList<T>> {
            val awaitAllAndReduceToList: () -> PersistentList<T> = { ->
                val futuresList: PersistentList<CompletableFuture<out T>> =
                    futuresStream.reduce(
                        persistentListOf(),
                        { pl, kf -> kf.fold { cs, _ -> pl.add(cs.toCompletableFuture()) } },
                        { pl1, pl2 -> pl1.addAll(pl2) }
                    )
                CompletableFuture.allOf(*futuresList.toTypedArray()).join()
                futuresList
                    .stream()
                    .map { cf: CompletableFuture<out T> -> cf.join() }
                    .reduceToPersistentList()
            }
            return of(CompletableFuture.supplyAsync(awaitAllAndReduceToList, executor), executor)
        }

        /**
         *
         * kotlin translates exceptionally/exceptionallyAsync/... methods into (Throwable) ->
         * Nothing! so handle(Async) must be used instead reified T type parameter specified and
         * inlined function since type T is in the input position as the output of the mapper
         * function
         */
        inline fun <reified T> KFuture<T>.mapFailure(
            crossinline mapper: (Throwable) -> T
        ): KFuture<T> {
            return this.flatMapFailure { thr: Throwable ->
                WrappedCompletionStageAndExecutor(
                    CompletableFuture.completedFuture(mapper.invoke(thr))
                )
            }
        }

        /**
         *
         * kotlin translates exceptionally/exceptionallyAsync/... methods into (Throwable) ->
         * Nothing! so handle(Async) must be used instead
         */
        inline fun <reified T> KFuture<T>.flatMapFailure(
            crossinline mapper: (Throwable) -> KFuture<T>
        ): KFuture<T> {
            val handleFunctionCall: (T?, Throwable?) -> KFuture<T> =
                { t: T?, throwable: Throwable? ->
                    if (throwable != null) {
                        mapper.invoke(throwable)
                    } else if (t != null) {
                        WrappedCompletionStageAndExecutor(CompletableFuture.completedFuture(t))
                    } else {
                        WrappedCompletionStageAndExecutor(
                            CompletableFuture.failedFuture(
                                IllegalArgumentException("completed_value is null")
                            )
                        )
                    }
                }
            return when (this) {
                is WrappedCompletionStageAndExecutor -> {
                    when {
                        this.executorOpt.isDefined() -> {
                            WrappedCompletionStageAndExecutor(
                                this.completionStage
                                    .handleAsync(handleFunctionCall, this.executorOpt.orNull()!!)
                                    .thenCompose { kf -> kf.toCompletionStage() },
                                this.executorOpt
                            )
                        }
                        else -> {
                            WrappedCompletionStageAndExecutor(
                                this.completionStage.handleAsync(handleFunctionCall).thenCompose {
                                    kf ->
                                    kf.toCompletionStage()
                                },
                                none()
                            )
                        }
                    }
                }
                else -> {
                    throw IllegalStateException(
                        "unhandled container type: [ ${this::class.qualifiedName} ]"
                    )
                }
            }
        }

        inline fun <reified T, X : Exception> KFuture<T>.flatMapFailureOfType(
            exceptionType: Class<X>,
            crossinline mapper: (X) -> KFuture<T>
        ): KFuture<T> {
            val handleFunctionCall: (T?, Throwable?) -> KFuture<T> =
                { t: T?, throwable: Throwable? ->
                    if (throwable != null && exceptionType.isInstance(throwable)) {
                        mapper.invoke(exceptionType.cast(throwable))
                    } else if (throwable != null) {
                        WrappedCompletionStageAndExecutor(CompletableFuture.failedFuture(throwable))
                    } else if (t != null) {
                        WrappedCompletionStageAndExecutor(CompletableFuture.completedFuture(t))
                    } else {
                        WrappedCompletionStageAndExecutor(
                            CompletableFuture.failedFuture(
                                IllegalArgumentException("completed_value is null")
                            )
                        )
                    }
                }
            return when (this) {
                is WrappedCompletionStageAndExecutor -> {
                    when {
                        this.executorOpt.isDefined() -> {
                            WrappedCompletionStageAndExecutor(
                                this.completionStage
                                    .handleAsync(handleFunctionCall, this.executorOpt.orNull()!!)
                                    .thenCompose { kf -> kf.toCompletionStage() },
                                this.executorOpt
                            )
                        }
                        else -> {
                            WrappedCompletionStageAndExecutor(
                                this.completionStage.handleAsync(handleFunctionCall).thenCompose {
                                    kf ->
                                    kf.toCompletionStage()
                                },
                                none()
                            )
                        }
                    }
                }
                else -> {
                    throw IllegalStateException(
                        "unhandled container type: [ ${this::class.qualifiedName} ]"
                    )
                }
            }
        }
    }

    fun filter(condition: (T) -> Boolean): KFuture<Option<T>> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                { of(completionStage.thenApply { t: T -> t.toOption().filter(condition) }) },
                { exec: Executor ->
                    of(
                        completionStage.thenApplyAsync(
                            { t: T -> t.toOption().filter(condition) },
                            exec
                        ),
                        exec
                    )
                }
            )
        }
    }

    fun filterNot(condition: (T) -> Boolean): KFuture<Option<T>> {
        return filter(condition.negate<T>())
    }

    fun filter(condition: (T) -> Boolean, ifConditionUnmet: (T) -> Throwable): KFuture<T> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                {
                    of(
                        completionStage.thenCompose { t: T ->
                            if (condition.invoke(t)) {
                                CompletableFuture.completedFuture<T>(t)
                            } else {
                                CompletableFuture.failedFuture<T>(ifConditionUnmet.invoke(t))
                            }
                        }
                    )
                },
                { exec: Executor ->
                    of(
                        completionStage.thenComposeAsync(
                            { t: T ->
                                if (condition.invoke(t)) {
                                    CompletableFuture.completedFuture<T>(t)
                                } else {
                                    CompletableFuture.failedFuture<T>(ifConditionUnmet.invoke(t))
                                }
                            },
                            exec
                        ),
                        exec
                    )
                }
            )
        }
    }

    fun filterNot(condition: (T) -> Boolean, ifConditionMet: (T) -> Throwable): KFuture<T> {
        return filter(condition.negate<T>(), ifConditionMet)
    }

    fun <R> map(mapper: (T) -> R): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                { of(completionStage.thenApply(mapper)) },
                { exec: Executor -> of(completionStage.thenApplyAsync(mapper, exec), exec) }
            )
        }
    }

    fun <R> map(executor: Executor, mapper: (T) -> R): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                { of(completionStage.thenApplyAsync(mapper, executor), executor) },
                { _: Executor -> of(completionStage.thenApplyAsync(mapper, executor), executor) }
            )
        }
    }

    fun <R> flatMap(mapper: (T) -> KFuture<R>): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                {
                    of(
                        completionStage.thenCompose { t: T ->
                            mapper.invoke(t).fold<CompletionStage<out R>> { cs, _ -> cs }
                        }
                    )
                },
                { exec: Executor ->
                    of(
                        completionStage.thenComposeAsync { t: T ->
                            mapper.invoke(t).fold<CompletionStage<out R>> { cs, _ -> cs }
                        },
                        exec
                    )
                }
            )
        }
    }

    fun <R> flatMap(executor: Executor, mapper: (T) -> KFuture<R>): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                {
                    of(
                        completionStage.thenComposeAsync(
                            { t: T ->
                                mapper.invoke(t).fold<CompletionStage<out R>> { cs, _ -> cs }
                            },
                            executor
                        ),
                        executor
                    )
                },
                { _: Executor ->
                    of(
                        completionStage.thenComposeAsync(
                            { t: T ->
                                mapper.invoke(t).fold<CompletionStage<out R>> { cs, _ -> cs }
                            },
                            executor
                        ),
                        executor
                    )
                }
            )
        }
    }

    fun <R> flatMapCompletionStage(mapper: (T) -> CompletionStage<out R>): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                { of(completionStage.thenCompose { t: T -> mapper.invoke(t) }) },
                { exec: Executor ->
                    of(completionStage.thenComposeAsync({ t: T -> mapper.invoke(t) }, exec), exec)
                }
            )
        }
    }

    fun <R> flatMapCompletionStage(
        executor: Executor,
        mapper: (T) -> CompletionStage<out R>
    ): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                {
                    of(
                        completionStage.thenComposeAsync({ t: T -> mapper.invoke(t) }, executor),
                        executor
                    )
                },
                { _: Executor ->
                    of(
                        completionStage.thenComposeAsync({ t: T -> mapper.invoke(t) }, executor),
                        executor
                    )
                }
            )
        }
    }

    fun <R> flatMapMono(mapper: (T) -> Mono<out R>): KFuture<R> {
        return fold { completionStage: CompletionStage<out T>, executorOpt: Option<Executor> ->
            executorOpt.fold(
                { of(completionStage.thenCompose { t: T -> mapper.invoke(t).toFuture() }) },
                { exec: Executor ->
                    of(
                        completionStage.thenComposeAsync(
                            { t: T -> mapper.invoke(t).toFuture() },
                            exec
                        ),
                        exec
                    )
                }
            )
        }
    }

    fun <U, V> zip(other: KFuture<U>, zipper: (T, U) -> V): KFuture<V> {
        return fold { thisStage: CompletionStage<out T>, executorOpt1: Option<Executor> ->
            other.fold { otherStage: CompletionStage<out U>, executorOpt2: Option<Executor> ->
                when {
                    executorOpt1.isDefined() -> {
                        of(
                            thisStage.thenCombineAsync(otherStage, zipper, executorOpt1.orNull()!!),
                            executorOpt1.orNull()!!
                        )
                    }
                    executorOpt2.isDefined() -> {
                        of(
                            thisStage.thenCombineAsync(otherStage, zipper, executorOpt2.orNull()!!),
                            executorOpt2.orNull()!!
                        )
                    }
                    else -> {
                        of(thisStage.thenCombine(otherStage, zipper))
                    }
                }
            }
        }
    }

    fun <U, V> zip(executor: Executor, other: KFuture<U>, zipper: (T, U) -> V): KFuture<V> {
        return fold { thisStage: CompletionStage<out T>, _: Option<Executor> ->
            other.fold { otherStage: CompletionStage<out U>, _: Option<Executor> ->
                of(thisStage.thenCombineAsync(otherStage, zipper, executor), executor)
            }
        }
    }

    fun <U, V, W> zip2(other1: KFuture<U>, other2: KFuture<V>, zipper: (T, U, V) -> W): KFuture<W> {
        return fold { thisStage: CompletionStage<out T>, executorOpt1: Option<Executor> ->
            other1.fold { otherStage1: CompletionStage<out U>, executorOpt2: Option<Executor> ->
                other2.fold { otherStage2: CompletionStage<out V>, executorOpt3: Option<Executor> ->
                    val awaitAllAndZipFunction: () -> W = { ->
                        val thisFuture = thisStage.toCompletableFuture()
                        val otherFuture1 = otherStage1.toCompletableFuture()
                        val otherFuture2 = otherStage2.toCompletableFuture()
                        CompletableFuture.allOf(thisFuture, otherFuture1, otherFuture2).join()
                        zipper.invoke(thisFuture.join(), otherFuture1.join(), otherFuture2.join())
                    }
                    when {
                        executorOpt1.isDefined() -> {
                            of(
                                CompletableFuture.supplyAsync(
                                    awaitAllAndZipFunction,
                                    executorOpt1.orNull()!!
                                ),
                                executorOpt1.orNull()!!
                            )
                        }
                        executorOpt2.isDefined() -> {
                            of(
                                CompletableFuture.supplyAsync(
                                    awaitAllAndZipFunction,
                                    executorOpt2.orNull()!!
                                ),
                                executorOpt2.orNull()!!
                            )
                        }
                        executorOpt3.isDefined() -> {
                            of(
                                CompletableFuture.supplyAsync(
                                    awaitAllAndZipFunction,
                                    executorOpt3.orNull()!!
                                ),
                                executorOpt3.orNull()!!
                            )
                        }
                        else -> {
                            of(CompletableFuture.supplyAsync(awaitAllAndZipFunction))
                        }
                    }
                }
            }
        }
    }

    fun <U, V, W> zip2(
        executor: Executor,
        other1: KFuture<U>,
        other2: KFuture<V>,
        zipper: (T, U, V) -> W
    ): KFuture<W> {
        return fold { thisStage: CompletionStage<out T>, _: Option<Executor> ->
            other1.fold { otherStage1: CompletionStage<out U>, _: Option<Executor> ->
                other2.fold { otherStage2: CompletionStage<out V>, _: Option<Executor> ->
                    val awaitAllAndZipFunction: () -> W = { ->
                        val thisFuture = thisStage.toCompletableFuture()
                        val otherFuture1 = otherStage1.toCompletableFuture()
                        val otherFuture2 = otherStage2.toCompletableFuture()
                        CompletableFuture.allOf(thisFuture, otherFuture1, otherFuture2).join()
                        zipper.invoke(thisFuture.join(), otherFuture1.join(), otherFuture2.join())
                    }
                    of(CompletableFuture.supplyAsync(awaitAllAndZipFunction, executor), executor)
                }
            }
        }
    }

    fun <U, V, W, X> zip3(
        other1: KFuture<U>,
        other2: KFuture<V>,
        other3: KFuture<W>,
        zipper: (T, U, V, W) -> X
    ): KFuture<X> {
        return fold { thisStage: CompletionStage<out T>, executorOpt1: Option<Executor> ->
            other1.fold { otherStage1: CompletionStage<out U>, executorOpt2: Option<Executor> ->
                other2.fold { otherStage2: CompletionStage<out V>, executorOpt3: Option<Executor> ->
                    other3.fold {
                        otherStage3: CompletionStage<out W>,
                        executorOpt4: Option<Executor> ->
                        val awaitAllAndZipFunction: () -> X = { ->
                            val thisFuture = thisStage.toCompletableFuture()
                            val otherFuture1 = otherStage1.toCompletableFuture()
                            val otherFuture2 = otherStage2.toCompletableFuture()
                            val otherFuture3 = otherStage3.toCompletableFuture()
                            CompletableFuture.allOf(
                                    thisFuture,
                                    otherFuture1,
                                    otherFuture2,
                                    otherFuture3
                                )
                                .join()
                            zipper.invoke(
                                thisFuture.join(),
                                otherFuture1.join(),
                                otherFuture2.join(),
                                otherFuture3.join()
                            )
                        }
                        when {
                            executorOpt1.isDefined() -> {
                                of(
                                    CompletableFuture.supplyAsync(
                                        awaitAllAndZipFunction,
                                        executorOpt1.orNull()!!
                                    ),
                                    executorOpt1.orNull()!!
                                )
                            }
                            executorOpt2.isDefined() -> {
                                of(
                                    CompletableFuture.supplyAsync(
                                        awaitAllAndZipFunction,
                                        executorOpt2.orNull()!!
                                    ),
                                    executorOpt2.orNull()!!
                                )
                            }
                            executorOpt3.isDefined() -> {
                                of(
                                    CompletableFuture.supplyAsync(
                                        awaitAllAndZipFunction,
                                        executorOpt3.orNull()!!
                                    ),
                                    executorOpt3.orNull()!!
                                )
                            }
                            executorOpt4.isDefined() -> {
                                of(
                                    CompletableFuture.supplyAsync(
                                        awaitAllAndZipFunction,
                                        executorOpt4.orNull()!!
                                    ),
                                    executorOpt4.orNull()!!
                                )
                            }
                            else -> {
                                of(CompletableFuture.supplyAsync(awaitAllAndZipFunction))
                            }
                        }
                    }
                }
            }
        }
    }

    fun onComplete(action: (T?, Throwable?) -> Unit): KFuture<T> {
        return fold { completionStage: CompletionStage<out T>, executorOption: Option<Executor> ->
            executorOption.fold(
                {
                    of(
                        completionStage.whenComplete { t: T?, thr: Throwable? ->
                            action.invoke(t, thr)
                        }
                    )
                },
                { exec: Executor ->
                    of(
                        completionStage.whenCompleteAsync(
                            { t: T?, thr: Throwable? -> action.invoke(t, thr) },
                            exec
                        ),
                        exec
                    )
                }
            )
        }
    }

    fun peek(ifSuccess: (T) -> Unit, ifFailure: (Throwable) -> Unit): KFuture<T> {
        return onComplete { t: T?, throwable: Throwable? ->
            when {
                t != null && throwable == null -> ifSuccess.invoke(t)
                t == null && throwable != null -> ifFailure.invoke(throwable)
                else -> {}
            }
        }
    }

    fun getWithin(amount: Long, timeunit: TimeUnit): Try<T> {
        return Try.attempt {
            fold { cs: CompletionStage<out T>, _: Option<Executor> ->
                cs.toCompletableFuture().get(amount, timeunit)
            }
        }
    }

    fun get(): Try<T> {
        return Try.attempt {
            fold { cs: CompletionStage<out T>, _: Option<Executor> ->
                cs.toCompletableFuture().join()
            }
        }
    }

    fun getOrElse(defaultValue: @UnsafeVariance T): T {
        return get().orElse(defaultValue)
    }

    fun getOrElseGet(defaultValueSupplier: () -> @UnsafeVariance T): T {
        return get().orElseGet(defaultValueSupplier)
    }

    fun getOrNull(): T? {
        return get().orNull()
    }

    fun getOrElseThrow(): T {
        return get().orElseThrow()
    }

    fun <X : Throwable> getOrElseThrow(mapper: (Throwable) -> X): T {
        return get().orElseThrow(mapper)
    }

    fun toMono(): Mono<out T> {
        return fold { stage: CompletionStage<out T>, _: Option<Executor> ->
            Mono.fromCompletionStage(stage)
        }
    }

    fun toCompletionStage(): CompletionStage<out T> {
        return fold { cs: CompletionStage<out T>, _: Option<Executor> -> cs }
    }

    override fun subscribe(s: Subscriber<in T>?) {
        if (s == null) {
            throw IllegalArgumentException("subscriber to ${KFuture::class.qualifiedName} is null")
        }
        return toMono().subscribe(s)
    }

    fun <R> fold(stageAndExecutor: (CompletionStage<out T>, Option<Executor>) -> R): R
}
