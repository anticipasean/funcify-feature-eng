package funcify.container.attempt

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption

import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.streams.asStream


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface Try<out S> {

    companion object {

        fun <S> success(successfulResult: S): Try<S> {
            return TryFactory.Success(successfulResult);
        }

        fun <S> failure(throwable: Throwable): Try<S> {
            return TryFactory.Failure<S>(throwable)
        }

        fun <S> nullableSuccess(successfulResult: S): Try<S> {
            return nullableSuccess(successfulResult) { NoSuchElementException("result is null") }
        }

        fun <S> nullableSuccess(nullableSuccessObject: S?, ifNull: () -> Throwable): Try<S> {
            return if (nullableSuccessObject == null) {
                failure(ifNull.invoke())
            } else {
                success(nullableSuccessObject)
            }
        }

        fun <S> nullableFailure(throwable: Throwable?): Try<S> {
            return if (throwable == null) {
                failure(NoSuchElementException("error/throwable is null"))
            } else {
                failure(throwable)
            }
        }

        fun emptySuccess(): Try<Unit> {
            return success(Unit)
        }

        fun <S> attempt(function: () -> S): Try<S> {
            return try {
                success(function.invoke())
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> attemptNullable(function: () -> S?): Try<Option<S>> {
            return try {
                success<Option<S>>(Option.fromNullable(function.invoke()))
            } catch (t: Throwable) {
                failure<Option<S>>(t)
            }
        }

        fun <S> attemptNullable(function: () -> S?, ifNull: () -> Throwable): Try<S> {
            return try {
                function.invoke()
                        .toOption()
                        .map { s -> success(s) }
                        .getOrElse { failure(ifNull.invoke()) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <I, O> lift(function: (I) -> O): (I) -> Try<O> {
            return { input: I ->
                try {
                    success<O>(function.invoke(input))
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }


        fun <I, O> liftNullable(function: (I) -> O?, ifNullResult: (I) -> Throwable): (I) -> Try<O> {
            return { input: I ->
                try {
                    val result: Option<O> = Option.fromNullable(function.invoke(input))
                    result.fold({
                                    failure(ifNullResult.invoke(input))
                                }, { o ->
                                    success(o)
                                })
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        fun <I, O> liftNullable(function: (I) -> O?): (I) -> Try<O> {
            return liftNullable(function) { input: I ->
                val message = """
                    |input [ type: ${input?.let { it::class.qualifiedName }} ] 
                    |resulted in null value for function
                    """
                IllegalArgumentException(message)
            }
        }

        fun <I1, I2, O> lift(function: (I1, I2) -> O): (I1, I2) -> Try<O> {
            return { i1: I1, i2: I2 ->
                try {
                    success<O>(function.invoke(i1, i2))
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        fun <I1, I2, O> liftNullable(function: (I1, I2) -> O?, ifNullResult: (I1, I2) -> Throwable): (I1, I2) -> Try<O> {
            return { i1: I1, i2: I2 ->
                try {
                    val result: Option<O> = Option.fromNullable(function.invoke(i1, i2))
                    result.fold({
                                    failure<O>(ifNullResult.invoke(i1, i2))
                                }, { output: O ->
                                    success(output)
                                })
                } catch (t: Throwable) {
                    failure<O>(t)
                }
            }
        }

        fun <I1, I2, O> liftNullable(function: (I1, I2) -> O?): (I1, I2) -> Try<O> {
            return liftNullable(function) { i1: I1, i2: I2 ->
                val message = """
                    |inputs [ i1.type: ${i1?.let { it::class.qualifiedName }}, 
                    |i2.type: ${i2?.let { it::class.qualifiedName }} ] 
                    |resulted in null value for function",
                """.trimMargin()
                IllegalArgumentException(message)
            }
        }

        fun <S> fromOptional(optional: Optional<out S>): Try<S> {
            return try {
                success<S>(optional.get())
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> fromOptional(optional: Optional<out S>, onOptionalEmpty: (NoSuchElementException) -> Throwable): Try<S> {
            return try {
                success<S>(optional.get())
            } catch (t: NoSuchElementException) {
                failure<S>(onOptionalEmpty.invoke(t))
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> fromOptional(optional: Optional<out S>, ifEmpty: () -> S): Try<S> {
            return try {
                optional.map { success(it) }
                        .orElseGet { success(ifEmpty.invoke()) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> fromOption(option: Option<S>): Try<S> {
            return fromOption(option) { nsee: NoSuchElementException -> nsee }
        }

        fun <S> fromOption(option: Option<S>, ifEmpty: (NoSuchElementException) -> Throwable): Try<S> {
            return try {
                option.fold({ failure(ifEmpty.invoke(NoSuchElementException("input option is empty"))) }) { input: S -> success(input) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> fromOption(option: Option<S>, ifEmpty: () -> S): Try<S> {
            return try {
                option.fold({ success(ifEmpty.invoke()) }) { input: S -> success(input) }
            } catch (t: Throwable) {
                failure<S>(t)
            }
        }

        fun <S> attemptRetryable(function: () -> S, numberOfRetries: Int): Try<S> {
            if (numberOfRetries < 0) {
                val message = """
                    |number_of_retries must be greater 
                    |than or equal to 0: [ actual: $numberOfRetries ]
                    |""".trimMargin()
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

        fun <S> attemptRetryableIf(function: () -> S, numberOfRetries: Int, failureCondition: (Throwable) -> Boolean): Try<S> {
            if (numberOfRetries < 0) {
                val message = """
                    |number_of_retries must be greater 
                    |than or equal to 0: [ actual: $numberOfRetries ]
                    |""".trimMargin()
                return failure<S>(IllegalArgumentException(message))
            }
            var attempt: Try<S> = attempt(function)
            var i = 0
            while (attempt.isFailure() && i < numberOfRetries) {
                attempt = attempt(function)
                if (attempt.isSuccess()) {
                    return attempt
                } else if (attempt.getFailure()
                                .filter(failureCondition)
                                .isEmpty()) {
                    return attempt
                }
                i++
            }
            return attempt
        }

        fun <S> attemptWithTimeout(function: () -> S, timeout: Long, unit: TimeUnit): Try<S> {
            val validatedTimeout = Math.max(0, timeout)
            return try {
                CompletableFuture.supplyAsync {
                    attempt(function)
                }
                        .get(validatedTimeout, unit)
            } catch (t: Throwable) {
                if (t is TimeoutException) {
                    val message = """
                        attempt_with_timeout: [ operation reached limit of 
                        $validatedTimeout 
                        ${unit.name.lowercase(Locale.getDefault())} ]
                        """.trimIndent()
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

        fun <S> attemptNullableWithTimeout(function: () -> S?, timeout: Long, unit: TimeUnit): Try<Option<S>> {
            val validatedTimeout = Math.max(0, timeout)
            return try {
                CompletableFuture.supplyAsync {
                    attemptNullable(function)
                }
                        .get(validatedTimeout, unit)
            } catch (t: Throwable) {
                if (t is TimeoutException) {
                    val message = String.format("attempt_with_timeout: [ operation reached limit of %d %s ]",
                                                validatedTimeout,
                                                unit.name.lowercase(Locale.getDefault()))
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
    }

    fun isSuccess(): Boolean {
        return fold({ true }) { false }
    }

    fun isFailure(): Boolean {
        return fold({ false }) { true }
    }

    fun getSuccess(): Option<S> {
        return fold({ value: S -> value.some() }) { none() }
    }

    fun getFailure(): Option<Throwable> {
        return fold({ none() }, { throwable: Throwable ->
            throwable.some()
        })
    }

    fun filter(condition: (S) -> Boolean, ifConditionUnmet: (S) -> Throwable): Try<S> {
        return fold({ s: S ->
                        try {
                            if (condition.invoke(s)) {
                                success<S>(s)
                            } else {
                                failure<S>(ifConditionUnmet.invoke(s))
                            }
                        } catch (t: Throwable) {
                            failure<S>(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <T : Any> ofKtType(targetType: KClass<T>, ifNotTargetType: () -> Throwable): Try<T> {
        return fold({ input: S ->
                        if (targetType.isInstance(input)) {
                            try {
                                success<T>(targetType.cast(input))
                            } catch (t: Throwable) {
                                failure<T>(t)
                            }
                        } else {
                            failure<T>(IllegalArgumentException("input is not instance of type ${targetType.simpleName}"))
                        }
                    }, { throwable: Throwable ->
                        failure<T>(throwable)
                    })
    }

    fun filter(condition: (S) -> Boolean): Try<Option<S>> {
        return fold({ s: S ->
                        try {
                            if (condition.invoke(s)) {
                                success(s.some())
                            } else {
                                success(none())
                            }
                        } catch (t: Throwable) {
                            failure(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <R> map(mapper: (S) -> R): Try<R> {
        return fold({ s: S ->
                        try {
                            success<R>(mapper.invoke(s))
                        } catch (t: Throwable) {
                            failure<R>(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <R> mapNullable(mapper: (S) -> R?, ifNull: () -> R): Try<R> {
        return fold({ input: S ->
                        try {
                            val result: Option<R> = Option.fromNullable(mapper.invoke(input))
                            result.fold({
                                            success<R>(ifNull.invoke())
                                        }) { r -> success<R>(r) }
                        } catch (t: Throwable) {
                            failure<R>(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <R> mapNullable(mapper: (S) -> R?): Try<Option<R>> {
        return fold({ input: S ->
                        try {
                            val result: Option<R> = Option.fromNullable(mapper.invoke(input))
                            result.fold({
                                            success(none<R>())
                                        }, { r ->
                                            success(r.some())
                                        })
                        } catch (t: Throwable) {
                            failure(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <F : Throwable> mapFailure(mapper: (Throwable) -> F): Try<S> {
        return fold({ input: S ->
                        success(input)
                    }, { throwable: Throwable ->
                        try {
                            failure<S>(mapper.invoke(throwable))
                        } catch (t: Throwable) {
                            failure<S>(t)
                        }
                    })
    }

    fun consume(consumer: (S) -> Unit): Try<Unit> {
        return fold({ input: S ->
                        try {
                            consumer.invoke(input)
                            emptySuccess()
                        } catch (t: Throwable) {
                            failure(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun consumeFailure(consumer: (Throwable) -> Unit): Try<Unit> {
        return fold({
                        emptySuccess()
                    }, { throwable: Throwable ->
                        try {
                            consumer.invoke(throwable)
                            emptySuccess()
                        } catch (t: Throwable) {
                            failure(t)
                        }
                    })
    }

    fun <R> flatMap(mapper: (S) -> Try<R>): Try<R> {
        return fold({ input: S ->
                        try {
                            mapper.invoke(input)
                        } catch (t: Throwable) {
                            failure<R>(t)
                        }
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    /**
     * @UnsafeVariance is needed since reference to S occurs in an
     * input position, a parameter in the Try output type of the mapper function
     */
    fun flatMapFailure(mapper: (Throwable) -> Try<@UnsafeVariance S>): Try<S> {
        return fold({ input: S ->
                        success(input)
                    }, { throwable: Throwable ->
                        try {
                            mapper.invoke(throwable)
                        } catch (t: Throwable) {
                            failure<S>(t)
                        }
                    })
    }

    fun <A, R> zip(otherAttempt: Try<A>, combiner: (S, A) -> R): Try<R> {
        return fold({ s: S ->
                        otherAttempt.fold({ a: A ->
                                              try {
                                                  success(combiner.invoke(s, a))
                                              } catch (t: Throwable) {
                                                  failure(t)
                                              }
                                          }, { throwable: Throwable ->
                                              failure(throwable)
                                          })
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <A, B, R> zip2(otherAttempt1: Try<A>, otherAttempt2: Try<B>, combiner: (S, A, B) -> R): Try<R> {
        return fold({ s: S ->
                        otherAttempt1.fold({ a: A ->
                                               otherAttempt2.fold({ b: B ->
                                                                      try {
                                                                          success(combiner.invoke(s, a, b))
                                                                      } catch (t: Throwable) {
                                                                          failure(t)
                                                                      }
                                                                  }, { throwable: Throwable ->
                                                                      failure(throwable)
                                                                  })
                                           }, { throwable: Throwable ->
                                               failure(throwable)
                                           })
                    }, { throwable: Throwable ->
                        failure(throwable)
                    })
    }

    fun <A, R> zip(optional: Optional<A>, combiner: (S, A) -> R): Try<R> {
        return zip(fromOptional<A>(optional), combiner)
    }

    fun <A, R> zip(option: Option<A>, combiner: (S, A) -> R): Try<R> {
        return zip(fromOption<A>(option), combiner)
    }

    fun orElse(defaultValue: @UnsafeVariance S): S {
        return fold({ input: S ->
                        input
                    }, {
                        defaultValue
                    })
    }

    /**
     * Enables caller to fetch the result value if successful
     *
     *
     * Only if an error occurred at some point in the processing chain before this call does this method then request a default
     * value from the supplier
     *
     * @param defaultValueSupplier - called if failure case
     * @return the result success value or the result of the default value supplier
     */
    fun orElseGet(defaultValueSupplier: () -> @UnsafeVariance S): S {
        return fold({ input: S ->
                        input
                    }, {
                        defaultValueSupplier.invoke()
                    })
    }

    /**
     * Enables caller to retrieve the result value directly, wrapping any error that may have occurred in the function application
     * chain before it is thrown
     *
     * @param exceptionWrapper - function that takes the error and wraps it in another error type
     * @param <F>              - any throwable
     * @return the result value if a success, or else throws an exception wrapping the error that occurred in the process
     * @throws F - the wrapper type for the exception
     */
    fun <F : Throwable> orElseThrow(exceptionWrapper: (Throwable) -> F): S {
        return fold({ input: S ->
                        input
                    }, { throwable: Throwable ->
                        throw exceptionWrapper.invoke(throwable)
                    })
    }

    /**
     * Enables caller to retrieve the result value directly, throwing any error that may have occurred in the function application
     * if it was an unchecked exception type or wrapping then throwing the error in a [java.lang.RuntimeException]
     *
     * @param <F> - some unchecked exception throwable
     * @return the result value if a success, or else throws an unchecked exception
     * @throws F - the unchecked exception type or a wrapped checked exception in a [java.lang.RuntimeException]
     */
    fun orElseThrow(): S {
        return fold({ input: S ->
                        input
                    }, { throwable: Throwable ->
                        throw throwable
                    })
    }

    fun ifFailed(errorHandler: (Throwable) -> Unit) {
        getFailure().fold({}) { throwable: Throwable ->
            errorHandler.invoke(throwable)
        }
    }

    fun ifSuccess(successHandler: (S) -> Unit) {
        getSuccess().fold({}) { input: S -> successHandler.invoke(input) }
    }

    /**
     * Provides the success value and a null value for the throwable if successful
     * Provides a null value for success and the throwable value if failed at some point in the processing chain
     *
     * @param handler
     */
    fun onComplete(handler: (S?, Throwable?) -> Unit) {
        fold({ input: S ->
                 attempt { handler.invoke(input, null) }
             }, { throwable: Throwable ->
                 attempt { handler.invoke(null, throwable) }
             })
    }

    fun stream(): Stream<out S> {
        return sequence().asStream()
    }

    fun sequence(): Sequence<S> {
        return fold({ input: S ->
                        sequenceOf(input)
                    }, {
                        emptySequence()
                    })
    }

    /**
     * Right is used to represent the success value per tradition since chained operations are typically
     * performed on the right value more often than the left value in an Either monad
     */
    fun either(): Either<Throwable, S> {
        return fold({ input: S -> input.right() }) { throwable: Throwable -> throwable.left() }
    }

    fun peek(successObserver: (S) -> Unit, failureObserver: (Throwable) -> Unit): Try<S> {
        return fold({ input: S ->
                        try {
                            successObserver.invoke(input)
                            success<S>(input)
                        } catch (t: Throwable) {
                            failure<S>(t)
                        }
                    }, { throwable: Throwable ->
                        try {
                            failureObserver.invoke(throwable)
                            failure<S>(throwable)
                        } catch (t: Throwable) {
                            failure<S>(t)
                        }
                    })
    }

    fun peekIfSuccess(successObserver: (S) -> Unit): Try<S> {
        return peek(successObserver) {}
    }

    fun peekIfFailure(failureObserver: (Throwable) -> Unit): Try<S> {
        return peek({}, failureObserver)
    }

    fun <R> transform(transformer: (Try<S>) -> R): R {
        return transformer.invoke(this)
    }

    /**
     * Although some functional frameworks prefer success values to be handled by the
     * right-hand side of the functional parameter set for folds,
     * here, the success handler is placed to the left of the failure handler since
     * typically in the method bodies where this is used, the success case and its return
     * value is of more significance than the failure case and its return value
     */
    fun <R> fold(successHandler: (S) -> R, failureHandler: (Throwable) -> R): R

}