package funcify.feature.tools.extensions

import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine

object ThrowableExtensions {

    fun Throwable.possiblyNestedHeadStackTraceElement(): StackTraceElement {
        var outerCause: Throwable = this
        var innerCause: Throwable? = this.cause
        while (innerCause != null) {
            outerCause = innerCause
            innerCause = innerCause.cause
        }
        return when (outerCause.stackTrace.size) {
            0 -> {
                throw IllegalArgumentException(
                    """throwable: [ type: ${outerCause::class.qualifiedName} ] 
                       |is missing a stacktrace from which to 
                       |extract a first element""".flattenIntoOneLine()
                )
            }
            else -> {
                outerCause.stackTrace[0]
            }
        }
    }
}
