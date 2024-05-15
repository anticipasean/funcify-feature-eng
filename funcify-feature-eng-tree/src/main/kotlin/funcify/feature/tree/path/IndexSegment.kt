package funcify.feature.tree.path

import arrow.core.Either
import arrow.core.left

data class IndexSegment(val index: Int): PathSegment {

    override fun toEither(): Either<Int, String> {
        return index.left()
    }

}
