package funcify.feature.tools.container.attempt

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import arrow.typeclasses.Monoid
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/7/22
 */
sealed interface Try<out S> : Iterable<S> {

    companion object {

        @JvmStatic
        fun <S> nullableSuccess(nullableSuccessObject: S?, ifNull: () -> Throwable): Try<S> {
            return if (nullableSuccessObject == null) {
                nullableFailure<S>(ifNull.invoke())
            } else {
                Success(nullableSuccessObject)
            }
        }

        @JvmStatic
        fun <S> nullableFailure(throwable: Throwable?): Try<S> {
            return if (throwable == null) {
                Failure<S>(NoSuchElementException("error/throwable is null"))
            } else {
                Failure<S>(throwable)
            }
        }

        @JvmStatic
        fun <S> success(successfulResult: S): Try<S> {
            return nullableSuccess(successfulResult)
        }

        @JvmStatic
        fun <S> failure(throwable: Throwable): Try<S> {
            return nullableFailure(throwable)
        }

        @JvmStatic
        fun <S> failure(function: () -> Throwable): Try<S> {
            return try {
                nullableFailure(function.invoke())
            } catch (t: Throwable) {
                nullableFailure(t)
            }
        }

        @JvmStatic
        fun <S> nullableSuccess(successfulResult: S?): Try<S> {
            return nullableSuccess(successfulResult) { NoSuchElementException("result is null") }
        }

        @JvmStatic
        fun emptySuccess(): Try<Unit> {
            return success(Unit)
        }

        @JvmStatic
        fun <S> attempt(function: () -> S): Try<S> {
            return try {
                success(function.invoke())
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> attemptNullable(function: () -> S?): Try<Option<S>> {
            return try {
                success<Option<S>>(Option.fromNullable(function.invoke()))
            } catch (t: Throwable) {
                failure<Option<S>>(t)
            }
        }

        @JvmStatic
        fun <S> attemptNullable(function: () -> S?, ifNull: () -> Throwable): Try<S> {
            return try {
                function
                    .invoke()
                    .toOption()
                    .map { s -> success(s) }
                    .getOrElse { failure(ifNull.invoke()) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <I, O> lift(function: (I) -> O): (I) -> Try<O> {
            return { input: I ->
                try {
                    success<O>(function.invoke(input))
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        @JvmStatic
        fun <I, O> liftNullable(
            function: (I) -> O?,
            ifNullResult: (I) -> Throwable
        ): (I) -> Try<O> {
            return { input: I ->
                try {
                    val result: Option<O> = Option.fromNullable(function.invoke(input))
                    result.fold({ failure(ifNullResult.invoke(input)) }, { o -> success(o) })
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        @JvmStatic
        fun <I, O> liftNullable(function: (I) -> O?): (I) -> Try<O> {
            return liftNullable(function) { input: I ->
                val message =
                    """
                    |input [ type: ${input?.let { it::class.qualifiedName }} ] 
                    |resulted in null value for function
                    """
                IllegalArgumentException(message)
            }
        }

        @JvmStatic
        fun <I1, I2, O> lift(function: (I1, I2) -> O): (I1, I2) -> Try<O> {
            return { i1: I1, i2: I2 ->
                try {
                    success<O>(function.invoke(i1, i2))
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        @JvmStatic
        fun <I1, I2, O> liftNullable(
            function: (I1, I2) -> O?,
            ifNullResult: (I1, I2) -> Throwable
        ): (I1, I2) -> Try<O> {
            return { i1: I1, i2: I2 ->
                try {
                    val result: Option<O> = Option.fromNullable(function.invoke(i1, i2))
                    result.fold(
                        { failure<O>(ifNullResult.invoke(i1, i2)) },
                        { output: O -> success(output) }
                    )
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        @JvmStatic
        fun <I1, I2, O> liftNullable(function: (I1, I2) -> O?): (I1, I2) -> Try<O> {
            return liftNullable(function) { i1: I1, i2: I2 ->
                val message =
                    """
                    |inputs [ i1.type: ${i1?.let { it::class.qualifiedName }}, 
                    |i2.type: ${i2?.let { it::class.qualifiedName }} ] 
                    |resulted in null value for function",
                """
                        .flatten()
                IllegalArgumentException(message)
            }
        }

        @JvmStatic
        fun <S> fromOptional(optional: Optional<out S>): Try<S> {
            return try {
                success<S>(optional.get())
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> fromOptional(
            optional: Optional<out S>,
            onOptionalEmpty: (NoSuchElementException) -> Throwable
        ): Try<S> {
            return try {
                success<S>(optional.get())
            } catch (t: NoSuchElementException) {
                failure<S>(onOptionalEmpty.invoke(t))
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> fromOptional(optional: Optional<out S>, ifEmpty: () -> S): Try<S> {
            return try {
                optional.map { s: S -> success(s) }.orElseGet { success(ifEmpty.invoke()) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> fromOption(option: Option<S>): Try<S> {
            return fromOption(option) { nsee: NoSuchElementException -> nsee }
        }

        @JvmStatic
        fun <S> fromOption(
            option: Option<S>,
            ifEmpty: (NoSuchElementException) -> Throwable
        ): Try<S> {
            return try {
                option.fold({
                    failure(ifEmpty.invoke(NoSuchElementException("input option is empty")))
                }) { input: S ->
                    success(input)
                }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> fromOption(option: Option<S>, ifEmpty: () -> S): Try<S> {
            return try {
                option.fold({ success(ifEmpty.invoke()) }) { input: S -> success(input) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> fromResult(result: Result<S>): Try<S> {
            return result.fold({ s -> Success(s) }, { t: Throwable -> Failure<S>(t) })
        }

        @JvmStatic
        fun <S> attemptRetryable(numberOfRetries: Int, function: () -> S): Try<S> {
            if (numberOfRetries < 0) {
                val message =
                    """
                    |number_of_retries must be greater 
                    |than or equal to 0: [ actual: $numberOfRetries ]
                    |"""
                        .flatten()
                return failure<S>(IllegalArgumentException(message))
            }
            var attempt: Try<S> = attempt(function)
            var i = 0
            while (attempt.isFailure() && i < numberOfRetries) {
                attempt = attempt(function)
                if (attempt.isSuccess()) {
                    return attempt
                }
                i++
            }
            return attempt
        }

        @JvmStatic
        fun <S> attemptRetryableIf(
            numberOfRetries: Int,
            function: () -> S,
            failureCondition: (Throwable) -> Boolean
        ): Try<S> {
            if (numberOfRetries < 0) {
                val message =
                    """
                    |number_of_retries must be greater 
                    |than or equal to 0: [ actual: $numberOfRetries ]
                    |"""
                        .flatten()
                return failure<S>(IllegalArgumentException(message))
            }
            var attempt: Try<S> = attempt(function)
            var i = 0
            while (attempt.isFailure() && i < numberOfRetries) {
                attempt = attempt(function)
                if (attempt.isSuccess()) {
                    return attempt
                } else if (attempt.getFailure().filter(failureCondition).isEmpty()) {
                    return attempt
                }
                i++
            }
            return attempt
        }

        @JvmStatic
        fun <S> attemptWithTimeout(timeout: Long, unit: TimeUnit, function: () -> S): Try<S> {
            val validatedTimeout = Math.max(0, timeout)
            return try {
                CompletableFuture.supplyAsync { attempt(function) }.get(validatedTimeout, unit)
            } catch (t: Throwable) {
                if (t is TimeoutException) {
                    val message =
                        """
                        attempt_with_timeout: [ operation reached limit of 
                        $validatedTimeout 
                        ${unit.name.lowercase(Locale.getDefault())} ]
                        """
                            .trimIndent()
                    return failure<S>(TimeoutException(message))
                }
                if (t is CompletionException) {
                    var thr = t
                    while (thr is CompletionException) {
                        thr = thr.cause!!
                    }
                    return failure<S>(thr)
                }
                failure<S>(t)
            }
        }

        @JvmStatic
        fun <S> attemptNullableWithTimeout(
            timeout: Long,
            unit: TimeUnit,
            function: () -> S?
        ): Try<Option<S>> {
            val validatedTimeout = Math.max(0, timeout)
            return try {
                CompletableFuture.supplyAsync { attemptNullable(function) }
                    .get(validatedTimeout, unit)
            } catch (t: Throwable) {
                if (t is TimeoutException) {
                    val message =
                        String.format(
                            "attempt_with_timeout: [ operation reached limit of %d %s ]",
                            validatedTimeout,
                            unit.name.lowercase(Locale.getDefault())
                        )
                    return failure<Option<S>>(TimeoutException(message))
                }
                if (t is CompletionException) {
                    var thr = t
                    while (thr is CompletionException) {
                        thr = thr.cause!!
                    }
                    return failure<Option<S>>(thr)
                }
                failure<Option<S>>(t)
            }
        }

        @JvmStatic
        fun <S> monoid(initial: Try<S>, combiner: (S, S) -> S): Monoid<Try<S>> {
            return TryMonoidFactory.HomogeneousSuccessTypeTryMonoid<S>(initial, combiner)
        }

        @JvmStatic
        fun <S, R> monoid(
            initial: Try<S>,
            mapper: (S) -> R,
            combiner: (R, R) -> R
        ): Monoid<Try<R>> {
            return TryMonoidFactory.HeterogeneousSuccessTypeTryMonoid<S, R>(
                initial,
                mapper,
                combiner
            )
        }

        @JvmStatic
        fun <S> attemptSequenceMonoid(): Monoid<Try<Sequence<S>>> {
            return monoid(success(emptySequence<S>())) { seq1, seq2 -> seq1 + seq2 }
        }

        @JvmStatic
        fun <SQ, T, S> attemptSequence(sequence: SQ): Try<Sequence<S>> where
        SQ : Sequence<T>,
        T : Try<S> {
            return sequence.fold(success(emptySequence<S>())) { acc: Try<Sequence<S>>, t: T ->
                acc.zip(t) { seq: Sequence<S>, s: S -> seq.plus(s) }
            }
        }

        @JvmStatic
        fun <I, T, S> attemptIterable(iterable: I): Try<Iterable<S>> where
        I : Iterable<T>,
        T : Try<S> {
            return iterable.fold(success(persistentListOf<S>())) { acc: Try<PersistentList<S>>, t: T
                ->
                acc.zip(t) { pl: PersistentList<S>, s: S -> pl.add(s) }
            }
        }

        @JvmStatic
        fun <S> attemptStream(stream: Stream<Try<S>>): Try<Stream<S>> {
            return stream
                .reduce(
                    success(Stream.builder<S>()),
                    { acc: Try<Stream.Builder<S>>, t: Try<S> ->
                        acc.flatMap { sBldr ->
                            t.map { s -> // Remove nulls for Kotlin compatibility
                                s.toOption().fold({ sBldr }, { succVal -> sBldr.add(succVal) })
                            }
                        }
                    },
                    { tryStrmBldr1: Try<Stream.Builder<S>>, tryStrmBldr2: Try<Stream.Builder<S>> ->
                        tryStrmBldr1.zip(tryStrmBldr2) {
                            strmBldr1: Stream.Builder<S>,
                            strmBldr2: Stream.Builder<S> ->
                            strmBldr2.build().forEach(strmBldr1::add)
                            strmBldr1
                        }
                    }
                )
                .map { strmBldr -> strmBldr.build() }
        }

        /**
         * Use of inline function to avoid @UnsafeVariance needed on "method" version of function
         * since reference to S occurs in an input position, a parameter in the Try output type of
         * the mapper function
         */
        inline fun <reified S> Try<S>.flatMapFailure(
            crossinline mapper: (Throwable) -> Try<S>
        ): Try<S> {
            return fold(
                { input: S -> Success(input) },
                { throwable: Throwable ->
                    try {
                        Option.fromNullable(mapper.invoke(throwable)).getOrElse {
                            Failure<S>(NoSuchElementException("result attempt is null"))
                        }
                    } catch (t: Throwable) {
                        Failure<S>(t)
                    }
                }
            )
        }

        inline fun <reified S> Try<S>.recoverFromFailure(
            crossinline mapper: (Throwable) -> Try<S>
        ): Try<S> = this.flatMapFailure(mapper)

        inline fun <reified T : Any> Try<*>.filterInstanceOf(): Try<T> {
            return this.fold(
                { input: Any? ->
                    when (input) {
                        is T -> {
                            Success(input)
                        }
                        else -> {
                            val messageSupplier: () -> String = { ->
                                "input is not instance of [ type: ${T::class.qualifiedName} ]"
                            }
                            Failure(IllegalArgumentException(messageSupplier.invoke()))
                        }
                    }
                },
                { throwable: Throwable -> Failure(throwable) }
            )
        }
    }

    fun isSuccess(): Boolean {
        return fold({ true }) { false }
    }

    fun isFailure(): Boolean {
        return fold({ false }) { true }
    }

    fun getSuccess(): Option<S> {
        return fold({ result: S -> result.some() }) { None }
    }

    fun getFailure(): Option<Throwable> {
        return fold({ None }, { throwable: Throwable -> throwable.some() })
    }

    fun filter(condition: (S) -> Boolean, ifConditionUnmet: (S) -> Throwable): Try<S> {
        return fold(
            { s: S ->
                try {
                    if (condition.invoke(s)) {
                        success<S>(s)
                    } else {
                        failure<S>(ifConditionUnmet.invoke(s))
                    }
                } catch (t: Throwable) {
                    failure<S>(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun filterNot(condition: (S) -> Boolean, ifConditionUnmet: (S) -> Throwable): Try<S> {
        return filter({ s: S -> !condition.invoke(s) }, ifConditionUnmet)
    }

    fun filter(condition: (S) -> Boolean): Try<Option<S>> {
        return fold(
            { s: S ->
                try {
                    if (condition.invoke(s)) {
                        success(s.some())
                    } else {
                        success(None)
                    }
                } catch (t: Throwable) {
                    failure(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun filterNot(condition: (S) -> Boolean): Try<Option<S>> {
        return filter { s: S -> !condition.invoke(s) }
    }

    fun <R> map(mapper: (S) -> R): Try<R> {
        return fold(
            { s: S ->
                try {
                    success<R>(mapper.invoke(s))
                } catch (t: Throwable) {
                    failure<R>(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <R> mapNullable(mapper: (S) -> R?, ifNull: () -> R): Try<R> {
        return fold(
            { input: S ->
                try {
                    Option.fromNullable(mapper.invoke(input)).fold({
                        success<R>(ifNull.invoke())
                    }) { r ->
                        success<R>(r)
                    }
                } catch (t: Throwable) {
                    failure<R>(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <R> mapNullable(mapper: (S) -> R?): Try<Option<R>> {
        return fold(
            { input: S ->
                try {
                    Option.fromNullable(mapper.invoke(input))
                        .fold({ success(None) }, { r -> success(r.some()) })
                } catch (t: Throwable) {
                    failure(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <F : Throwable> mapFailure(mapper: (Throwable) -> F): Try<S> {
        return fold(
            { input: S -> success(input) },
            { throwable: Throwable ->
                try {
                    failure<S>(mapper.invoke(throwable))
                } catch (t: Throwable) {
                    failure<S>(t)
                }
            }
        )
    }

    fun consume(consumer: (S) -> Unit): Try<Unit> {
        return fold(
            { input: S ->
                try {
                    consumer.invoke(input)
                    emptySuccess()
                } catch (t: Throwable) {
                    failure(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun consumeFailure(consumer: (Throwable) -> Unit): Try<Unit> {
        return fold(
            { _: S -> emptySuccess() },
            { throwable: Throwable ->
                try {
                    consumer.invoke(throwable)
                    emptySuccess()
                } catch (t: Throwable) {
                    failure(t)
                }
            }
        )
    }

    fun <R> flatMap(mapper: (S) -> Try<R>): Try<R> {
        return fold(
            { input: S ->
                try {
                    mapper.invoke(input)
                } catch (t: Throwable) {
                    failure<R>(t)
                }
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <A, R> zip(otherAttempt: Try<A>, combiner: (S, A) -> R): Try<R> {
        return fold(
            { s: S ->
                otherAttempt.fold(
                    { a: A ->
                        try {
                            success(combiner.invoke(s, a))
                        } catch (t: Throwable) {
                            failure(t)
                        }
                    },
                    { throwable: Throwable -> failure(throwable) }
                )
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <A> zip(otherAttempt: Try<A>): Try<Pair<S, A>> {
        return zip(otherAttempt) { s, a -> s to a }
    }

    fun <A, B, R> zip2(
        otherAttempt1: Try<A>,
        otherAttempt2: Try<B>,
        combiner: (S, A, B) -> R
    ): Try<R> {
        return fold(
            { s: S ->
                otherAttempt1.fold(
                    { a: A ->
                        otherAttempt2.fold(
                            { b: B ->
                                try {
                                    success(combiner.invoke(s, a, b))
                                } catch (t: Throwable) {
                                    failure(t)
                                }
                            },
                            { throwable: Throwable -> failure(throwable) }
                        )
                    },
                    { throwable: Throwable -> failure(throwable) }
                )
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <A, B> zip2(otherAttempt1: Try<A>, otherAttempt2: Try<B>): Try<Triple<S, A, B>> {
        return zip2(otherAttempt1, otherAttempt2) { s, a, b -> Triple(s, a, b) }
    }

    fun <A, B, C, R> zip3(
        otherAttempt1: Try<A>,
        otherAttempt2: Try<B>,
        otherAttempt3: Try<C>,
        combiner: (S, A, B, C) -> R
    ): Try<R> {
        return fold(
            { s: S ->
                otherAttempt1.fold(
                    { a: A ->
                        otherAttempt2.fold(
                            { b: B ->
                                otherAttempt3.fold(
                                    { c: C ->
                                        try {
                                            success(combiner.invoke(s, a, b, c))
                                        } catch (t: Throwable) {
                                            failure(t)
                                        }
                                    },
                                    { throwable: Throwable -> failure(throwable) }
                                )
                            },
                            { throwable: Throwable -> failure(throwable) }
                        )
                    },
                    { throwable: Throwable -> failure(throwable) }
                )
            },
            { throwable: Throwable -> failure(throwable) }
        )
    }

    fun <A, R> zip(optional: Optional<A>, combiner: (S, A) -> R): Try<R> {
        return zip(fromOptional<A>(optional), combiner)
    }

    fun <A, R> zip(option: Option<A>, combiner: (S, A) -> R): Try<R> {
        return zip(fromOption<A>(option), combiner)
    }

    fun <A> zip(option: Option<A>): Try<Pair<S, A>> {
        return zip(fromOption<A>(option)) { s, a -> s to a }
    }

    fun <A, B, R> zip2(option1: Option<A>, option2: Option<B>, combiner: (S, A, B) -> R): Try<R> {
        return zip2(fromOption<A>(option1), fromOption<B>(option2), combiner)
    }

    fun <A, B, C, R> zip3(
        option1: Option<A>,
        option2: Option<B>,
        option3: Option<C>,
        combiner: (S, A, B, C) -> R
    ): Try<R> {
        return zip3(
            fromOption<A>(option1),
            fromOption<B>(option2),
            fromOption<C>(option3),
            combiner
        )
    }

    fun orNull(): S? {
        return fold({ result: S -> result }, { _: Throwable -> null })
    }

    fun orElse(defaultValue: @UnsafeVariance S): S {
        return fold({ result: S -> result }, { _: Throwable -> defaultValue })
    }

    /**
     * Enables caller to fetch the result value if successful
     *
     * Only if an error occurred at some point in the processing chain before this call does this
     * method then request a default value from the supplier
     *
     * @param defaultValueSupplier
     * - called if failure case
     *
     * @return the result success value or the result of the default value supplier
     */
    fun orElseGet(defaultValueSupplier: () -> @UnsafeVariance S): S {
        return fold({ result: S -> result }, { _: Throwable -> defaultValueSupplier.invoke() })
    }

    /**
     * Enables caller to retrieve the result value directly, wrapping any error that may have
     * occurred in the function application chain before it is thrown
     *
     * @param exceptionWrapper
     * - function that takes the error and wraps it in another error type
     *
     * @param <F> - any throwable
     * @return the result value if a success, or else throws an exception wrapping the error that
     *   occurred in the process
     * @throws F
     * - the wrapper type for the exception
     */
    fun <F : Throwable> orElseThrow(exceptionWrapper: (Throwable) -> F): S {
        return fold(
            { result: S -> result },
            { throwable: Throwable -> throw exceptionWrapper.invoke(throwable) }
        )
    }

    /**
     * Enables caller to retrieve the result value directly, throwing any error that may have
     * occurred in the function application if it was an unchecked exception type or wrapping then
     * throwing the error in a [java.lang.RuntimeException]
     *
     * @param <F> - some unchecked exception throwable
     * @return the result value if a success, or else throws an unchecked exception
     * @throws F
     * - the unchecked exception type or a wrapped checked exception in a
     *   [java.lang.RuntimeException]
     */
    fun orElseThrow(): S {
        return fold({ result: S -> result }, { throwable: Throwable -> throw throwable })
    }

    fun orElseTry(otherAttempt: () -> Try<@UnsafeVariance S>): Try<S> {
        return fold({ result: S -> Try.success(result) }, { _: Throwable -> otherAttempt.invoke() })
    }

    fun orElseTry(
        otherAttempt: () -> Try<@UnsafeVariance S>,
        failureCombiner: (Throwable, Throwable) -> Throwable
    ): Try<S> {
        return fold(
            { result: S -> Try.success(result) },
            { t1: Throwable ->
                otherAttempt.invoke().mapFailure { t2: Throwable -> failureCombiner.invoke(t1, t2) }
            }
        )
    }

    fun ifFailed(errorHandler: (Throwable) -> Unit) {
        getFailure().fold({}) { throwable: Throwable -> errorHandler.invoke(throwable) }
    }

    fun ifSuccess(successHandler: (S) -> Unit) {
        getSuccess().fold({}) { result: S -> successHandler.invoke(result) }
    }

    /**
     * Provides the success value and a null value for the throwable if successful Provides a null
     * value for success and the throwable value if failed at some point in the processing chain
     *
     * @param handler
     */
    fun handleEither(handler: (S?, Throwable?) -> Unit) {
        fold(
            { result: S -> attempt { handler.invoke(result, null) } },
            { throwable: Throwable -> attempt { handler.invoke(null, throwable) } }
        )
    }

    fun stream(): Stream<out S> {
        return fold({ result: S -> Stream.of(result) }, { _: Throwable -> Stream.empty() })
    }

    fun sequence(): Sequence<S> {
        return fold({ result: S -> sequenceOf(result) }, { _: Throwable -> emptySequence() })
    }

    fun peek(successObserver: (S) -> Unit, failureObserver: (Throwable) -> Unit): Try<S> {
        return fold(
            { result: S ->
                try {
                    successObserver.invoke(result)
                    success<S>(result)
                } catch (t: Throwable) {
                    // ignore any throwable that occurs within peek
                    success<S>(result)
                }
            },
            { throwable: Throwable ->
                try {
                    failureObserver.invoke(throwable)
                    failure<S>(throwable)
                } catch (t: Throwable) {
                    // ignore any throwable that occurs within peek
                    failure<S>(throwable)
                }
            }
        )
    }

    fun peekIfSuccess(successObserver: (S) -> Unit): Try<S> {
        return peek(successObserver) {}
    }

    fun peekIfFailure(failureObserver: (Throwable) -> Unit): Try<S> {
        return peek({}, failureObserver)
    }

    override fun iterator(): Iterator<S> {
        return fold({ result: S -> sequenceOf(result) }, { _: Throwable -> emptySequence() })
            .iterator()
    }

    /**
     * Right is used to represent the success value per tradition since chained operations are
     * typically performed on the right value more often than the left value in an Either monad
     */
    fun toEither(): Either<Throwable, S> {
        return fold({ result: S -> result.right() }) { throwable: Throwable -> throwable.left() }
    }

    fun toResult(): Result<S> {
        return fold(
            { result: S -> Result.success(result) },
            { throwable: Throwable -> Result.failure(throwable) }
        )
    }

    fun toMono(): Mono<out S> {
        return fold({ result: S -> Mono.just(result) }, { t: Throwable -> Mono.error(t) })
    }

    fun toFlux(): Flux<out S> {
        return fold({ result: S -> Flux.just(result) }, { t: Throwable -> Flux.error(t) })
    }

    fun toKFuture(): KFuture<S> {
        return fold(
            { result: S -> KFuture.completed(result) },
            { t: Throwable -> KFuture.failed(t) }
        )
    }

    /**
     * Although some functional frameworks prefer success values to be handled by the right-hand
     * side of the functional parameter set for folds, here, the success handler is placed to the
     * left of the failure handler since typically in the method bodies where this is used, the
     * success case and its return value is of more significance than the failure case and its
     * return value. Type inference will therefore attempt to extract the type information from the
     * left return value before the right return value and would otherwise prompt the caller to add
     * `Try.failure<SUCCESS_TYPE_PARAMETER>` to the failure case if the two positions were swapped
     */
    fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R
}
