package funcify.feature.util

internal object StringExtensions {

    fun String.flatten(marginPrefix: String = "|"): String {
        return when (this.length) {
            0 -> {
                this
            }
            else -> {
                this.trimMargin(marginPrefix).replace(System.lineSeparator(), "")
            }
        }
    }
}
