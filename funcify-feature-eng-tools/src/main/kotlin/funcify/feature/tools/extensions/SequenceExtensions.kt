package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tools.control.TraversalFunctions

object SequenceExtensions {

    fun <T, O : Option<T>> Sequence<O>.flatMapOptions(): Sequence<T> {
        return this.flatMap { opt: Option<T> -> opt.fold(::emptySequence, ::sequenceOf) }
    }

    fun <L, R> Sequence<L>.recurse(function: (L) -> Sequence<Either<L, R>>): Sequence<R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithSequence(l, function) }
    }
}
