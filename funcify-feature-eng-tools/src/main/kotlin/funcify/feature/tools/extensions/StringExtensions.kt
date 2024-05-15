package funcify.feature.tools.extensions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
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

    private val camelCaseTranslator: (String) -> String by lazy {
        val cache: ConcurrentMap<String, String> = ConcurrentHashMap()
        val convertToCamelCase: (String) -> String = { str: String ->
            when {
                str.indexOf('_') >= 0 || str.indexOf(' ') >= 0 -> {
                    str.splitToSequence(' ', '_')
                        .filter(String::isNotBlank)
                        .fold(StringBuilder()) { sb: StringBuilder, s: String ->
                            when {
                                sb.length == 0 && s.first().isUpperCase() -> {
                                    sb.append(s.replaceFirstChar { c: Char -> c.lowercase() })
                                }
                                sb.length != 0 && s.first().isLowerCase() -> {
                                    sb.append(s.replaceFirstChar { c: Char -> c.uppercase() })
                                }
                                else -> {
                                    sb.append(s)
                                }
                            }
                        }
                        .toString()
                }
                else -> {
                    str
                }
            }
        }
        { str: String -> cache.computeIfAbsent(str, convertToCamelCase) }
    }

    fun String.toCamelCase(): String {
        return camelCaseTranslator(this)
    }
}
