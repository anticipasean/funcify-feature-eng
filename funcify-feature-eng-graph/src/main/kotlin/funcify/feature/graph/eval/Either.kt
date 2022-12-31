package funcify.feature.graph.eval

/**
 *
 * @author smccarron
 * @created 2022-12-31
 */
sealed interface Either<out L, out R> {

    companion object {

        data class Left<out L, out R>(val value: L) : Either<L, R>

        data class Right<out L, out R>(val value: R) : Either<L, R>

        @JvmStatic
        fun <L, R> left(value: L): Either<L, R> {
            return Left(value)
        }

        @JvmStatic
        fun <L, R> right(value: R): Either<L, R> {
            return Right(value)
        }

        fun <L, R> recurse(input: L, function: (L) -> Either<L, R>): R {
            var result: Either<L, R> = function(input)
            while (result is Left) {
                result = result.flatMapLeft(function)
            }
            return when (result) {
                is Right -> {
                    result.value
                }
                else -> {
                    throw IllegalStateException("unexpected state: loop prematurely terminated")
                }
            }
        }
    }

    fun <V> map(mapper: (R) -> V): Either<L, V> {
        return when (this) {
            is Left -> {
                @Suppress("UNCHECKED_CAST") //
                this as Left<L, V>
            }
            is Right -> {
                Right(mapper(this.value))
            }
        }
    }

    fun <V> flatMap(mapper: (R) -> Either<@UnsafeVariance L, V>): Either<L, V> {
        return when (this) {
            is Left -> {
                @Suppress("UNCHECKED_CAST") //
                this as Left<L, V>
            }
            is Right -> {
                mapper(this.value)
            }
        }
    }

    fun <V> mapLeft(mapper: (L) -> V): Either<V, R> {
        return when (this) {
            is Left -> {
                Left(mapper(this.value))
            }
            is Right -> {
                @Suppress("UNCHECKED_CAST") //
                this as Right<V, R>
            }
        }
    }

    fun <V> flatMapLeft(mapper: (L) -> Either<V, @UnsafeVariance R>): Either<V, R> {
        return when (this) {
            is Left -> {
                mapper(this.value)
            }
            is Right -> {
                @Suppress("UNCHECKED_CAST") //
                this as Right<V, R>
            }
        }
    }

    fun <V> fold(leftMapper: (L) -> V, rightMapper: (R) -> V): V {
        return when (this) {
            is Left -> {
                leftMapper(this.value)
            }
            is Right -> {
                rightMapper(this.value)
            }
        }
    }
}
