package funcify.feature.tools.extensions

import funcify.feature.tools.extensions.StringExtensions.flatten

object ThrowableExtensions {

    fun Throwable.possiblyNestedHeadStackTraceElement(): StackTraceElement {
        var t: Throwable? = this
        while (t?.cause != null) {
            t = t.cause
        }
        return when (t?.stackTrace?.size) {
            null,
            0 -> {
                throw IllegalArgumentException(
                    """throwable: [ type: ${this::class.qualifiedName} ] 
                       |is missing a stacktrace from which to 
                       |extract a first element""".flatten()
                )
            }
            else -> {
                t.stackTrace[0]
            }
        }
    }
}
