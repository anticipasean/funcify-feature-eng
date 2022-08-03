package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import arrow.core.none
import funcify.feature.tools.control.TraversalFunctions
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object OptionExtensions {

    fun <T> Option<T>.stream(): Stream<T> {
        return this.fold({ Stream.empty() }, { t -> Stream.ofNullable(t) })
    }

    fun <L, R> Option<L>.recurse(function: (L) -> Option<Either<L, R>>): Option<R> {
        return this.fold({ none() }, { l: L -> TraversalFunctions.recurseWithOption(l, function) })
    }

    fun <T> Option<T>.toPersistentSet(): PersistentSet<T> {
        return this.fold({ persistentSetOf() }, { t: T -> persistentSetOf(t) })
    }
}
