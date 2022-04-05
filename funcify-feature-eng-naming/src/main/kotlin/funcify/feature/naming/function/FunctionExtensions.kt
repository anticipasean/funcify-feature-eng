package funcify.feature.naming.function


/**
 *
 * @author smccarron
 * @created 3/26/22
 */
internal object FunctionExtensions {

    inline fun <A, B, C> ((A) -> B).andThen(crossinline function: (B) -> C): (A) -> C {
        return { a: A ->
            function.invoke(this.invoke(a))
        }
    }

    inline fun <A, B, C, D> ((A, B) -> C).andThen(crossinline function: (C) -> D): (A, B) -> D {
        return { a: A, b: B ->
            function.invoke(this.invoke(a,
                                        b))
        }
    }

    fun <T> ((T) -> Boolean).negate(): (T) -> Boolean {
        return { t ->
            !this.invoke(t)
        }
    }

}