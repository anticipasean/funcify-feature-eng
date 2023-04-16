package funcify.feature.tree.path

import arrow.core.Either
import arrow.core.right

data class NameSegment(val name: String): PathSegment {

    override fun toEither(): Either<Int, String> {
        return name.right()
    }

}
