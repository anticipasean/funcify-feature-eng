package funcify.feature.tree.path

import arrow.core.Either

/**
 *
 * @author smccarron
 * @created 2023-04-09
 */
sealed interface PathSegment {

    fun toEither(): Either<Int, String>

    fun <R> fold(indexMapper: (Int) -> R, nameMapper: (String) -> R): R {
        return toEither().fold(indexMapper, nameMapper)
    }
}
