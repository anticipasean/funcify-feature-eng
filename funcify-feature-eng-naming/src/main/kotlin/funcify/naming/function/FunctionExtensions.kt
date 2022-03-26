package funcify.naming.function


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

    fun <T> ((T) -> Boolean).negate(): (T) -> Boolean {
        return { t ->
            !this.invoke(t)
        }
    }

}