package funcify.naming

import funcify.naming.convention.DefaultNamingConventionFactory
import funcify.naming.convention.NamingConvention


/**
 *
 * @author smccarron
 * @created 3/26/22
 */
enum class StandardNamingConventions(private val namingConvention: NamingConvention<String>) : NamingConvention<String> {

    SNAKE_CASE(DefaultNamingConventionFactory.getInstance()
                       .createConventionForRawStrings()
                       .whenInputProvided {
                           extractOneOrMoreSegmentsWith { s ->
                               s.splitToSequence(Regex("\\s+|_+"))
                                       .asIterable()
                           }
                       }
                       .followConvention {
                           forEachSegment {
                               forAnyCharacter {
                                   transformByPosition {
                                       anyCharacter { c: Char -> c.isUpperCase() }.precededBy { c: Char -> c.isLowerCase() }
                                               .transformAndSplitIntoSeparateSegments { c: Char -> c.lowercase() }
                                   }
                                   transformAll { c: Char -> c.lowercaseChar() }
                               }
                           }
                       }
                       .named("SnakeCase"));

    override val conventionName: String
        get() = namingConvention.conventionName
    override val conventionKey: Any
        get() = this

    override fun deriveName(input: String): ConventionalName {
        return namingConvention.deriveName(input)
    }

}