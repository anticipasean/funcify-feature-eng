package funcify.feature.tools.extensions

/**
 *
 * @author smccarron
 * @created 3/7/22
 */
object StringExtensions {

    fun String.flatten(marginPrefix: String = "|"): String {
        return when {
            this.isEmpty() -> {
                this
            }
            else -> {
                this.trimMargin(marginPrefix).replace(System.lineSeparator(), "")
            }
        }
    }
}
