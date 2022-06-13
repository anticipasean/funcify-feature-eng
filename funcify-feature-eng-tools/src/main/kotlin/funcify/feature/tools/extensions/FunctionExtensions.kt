package funcify.feature.tools.extensions

object FunctionExtensions {

    inline fun <A, B, C> ((A) -> B).andThen(crossinline function: (B) -> C): (A) -> C {
        return { a: A -> function.invoke(this.invoke(a)) }
    }

    inline fun <A, B, C, D> ((A, B) -> C).andThen(crossinline function: (C) -> D): (A, B) -> D {
        return { a: A, b: B -> function.invoke(this.invoke(a, b)) }
    }

    inline fun <A, B, C> ((B) -> C).compose(crossinline function: (A) -> B): (A) -> C {
        return { a: A -> this.invoke(function.invoke(a)) }
    }

    inline fun <A, B, C, D, E> ((C, D) -> E).compose(
        crossinline function: (A, B) -> Pair<C, D>
    ): (A, B) -> E {
        return { a: A, b: B ->
            val (c, d) = function.invoke(a, b)
            this.invoke(c, d)
        }
    }
}
