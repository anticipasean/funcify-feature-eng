package funcify.feature.tools.extensions

object PredicateExtensions {

    fun <T> ((T) -> Boolean).negate(): (T) -> Boolean {
        return { t: T -> !this.invoke(t) }
    }

}
