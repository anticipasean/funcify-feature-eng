package funcify.feature.tools.string

import java.util.regex.Pattern


/**
 *
 * @author smccarron
 * @created 3/7/22
 */
enum class StringCase {

    UNKNOWN,
    SNAKE_CASE,
    PASCAL_CASE,
    CAMEL_CASE;

    internal enum class SplitTokenType(val pattern: Pattern) {
        WHITESPACE(Pattern.compile("(\\s+)([^\\s]+)")),
        HYPHENS(Pattern.compile("([^-]+)-([^-]+)")),
        UNDERSCORES(Pattern.compile("([^_]+)_([^_]+)")),
        LOWERCASE_UPPERCASE_NUMERIC_PAIRS(Pattern.compile("([a-z]+)([A-Z0-9]+)"))
    }

}