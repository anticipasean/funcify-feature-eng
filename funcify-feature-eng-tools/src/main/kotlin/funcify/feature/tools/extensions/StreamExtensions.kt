package funcify.feature.tools.extensions

import arrow.core.Either
import arrow.core.Option
import funcify.feature.tools.control.TraversalFunctions
import java.util.*
import java.util.stream.Stream

object StreamExtensions {

    fun <T, O : Option<T>> Stream<out O>.flatMapOptions(): Stream<out T> {
        return this.flatMap { opt: Option<T> ->
            opt.fold({ Stream.empty() }, { t: T -> Stream.ofNullable(t) })
        }
    }

    inline fun <reified T> Stream<*>.filterIsInstance(): Stream<T> {
        return this.flatMap { input: Any? ->
            when (input) {
                is T -> Stream.of(input)
                else -> Stream.empty()
            }
        }
    }

    fun <L, R> Stream<out L>.recurse(function: (L) -> Stream<out Either<L, R>>): Stream<out R> {
        return this.flatMap { l: L -> TraversalFunctions.recurseWithStream(l, function) }
    }

    fun <T> Stream<out T>.asIterable(): Iterable<T> {
        return Iterable<T> { this.iterator() }
    }
}
