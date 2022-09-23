package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import arrow.core.toOption
import funcify.feature.tools.control.TraversalFunctions

object SequenceExtensions {

    fun <T, O : Option<T>> Sequence<O>.flatMapOptions(): Sequence<T> {
        return this.flatMap { opt: Option<T> -> opt.fold(::emptySequence, ::sequenceOf) }
    }

    fun <T> Sequence<T>.firstOrNone(): Option<T> {
        return this.firstOrNull().toOption()
    }

    fun <T> Sequence<T>.firstOrNone(condition: (T) -> Boolean): Option<T> {
        return this.firstOrNull(condition).toOption()
    }

    fun <L, R> Sequence<L>.recurse(function: (L) -> Sequence<Either<L, R>>): Sequence<R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithSequence(l, function) }
    }
}
