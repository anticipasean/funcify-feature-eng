package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tools.control.TraversalFunctions
import java.util.stream.Stream

object StreamExtensions {

    fun <T, O : Option<T>> Stream<O>.flatMapOptions(): Stream<T> {
        return this.flatMap { opt: Option<T> ->
            opt.fold({ Stream.empty() }, { t: T -> Stream.ofNullable(t) })
        }
    }

    fun <L, R> Stream<L>.recurse(function: (L) -> Stream<Either<L, R>>): Stream<R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithStream(l, function) }
    }
}
