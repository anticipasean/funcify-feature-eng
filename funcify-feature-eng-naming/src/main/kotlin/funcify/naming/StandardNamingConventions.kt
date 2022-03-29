package funcify.naming

import funcify.naming.convention.DefaultNamingConventionFactory


/**
 *
 * @author smccarron
 * @created 3/26/22
 */
enum class StandardNamingConventions(private val namingConvention: NamingConvention<String>) : NamingConvention<String> {

    SNAKE_CASE(DefaultNamingConventionFactory.getInstance()
                       .createConventionForStringInput()
                       .whenInputProvided {
                           extractOneOrMoreSegmentsWith { s ->
                               s.splitToSequence(Regex("\\s+|_+"))
                                       .asIterable()
                           }
                       }
                       .followConvention {
                           forEachSegment {
                               forLeadingCharacters {
                                   stripAny { c: Char -> c.isWhitespace() }
                               }
                               forAnyCharacter {
                                   transformByWindow {
                                       anyCharacter { c: Char -> c.isUpperCase() }.precededBy { c: Char -> c.isLowerCase() }
                                               .transformInto { c: Char -> "_$c" }
                                   }
                                   transformByWindow {
                                       anyCharacter { c: Char -> c.isDigit() }.precededBy { c: Char -> c.isLowerCase() }
                                               .transformInto { c: Char -> "_$c" }
                                   }
                                   transformAll { c: Char -> c.lowercaseChar() }
                               }
                           }
                           furtherSegmentAnyWith('_')
                           joinSegmentsWith('_')
                       }
                       .named("SnakeCase")),

    CAMEL_CASE(DefaultNamingConventionFactory.getInstance()
                       .createConventionForStringInput()
                       .whenInputProvided {
                           extractOneOrMoreSegmentsWith { s ->
                               s.splitToSequence(Regex("\\s+|_+"))
                                       .asIterable()
                           }
                       }
                       .followConvention {
                           forEachSegment {
                               forAnyCharacter {
                                   transformByWindow {
                                       anyCharacter { c: Char -> c.isUpperCase() }.followedBy { c: Char -> c.isLowerCase() }
                                               .transformInto { c: Char -> "_$c" }
                                   }
                               }
                           }
                           furtherSegmentAnyWith('_')
                           forEachSegment {
                               forLeadingCharacters {
                                   stripAny { c: Char -> c.isWhitespace() }
                                   replaceFirstCharacterOfFirstSegmentIf({ c: Char -> c.isUpperCase() },
                                                                         { c: Char -> c.lowercase() })
                                   replaceFirstCharactersOfOtherSegmentsIf({ c: Char -> c.isLowerCase() },
                                                                           { c: Char -> c.uppercase() })
                               }
                           }
                           joinSegmentsWithoutAnyDelimiter()
                       }
                       .named("CamelCase")),
    PASCAL_CASE(DefaultNamingConventionFactory.getInstance()
                        .createConventionForStringInput()
                        .whenInputProvided {
                            extractOneOrMoreSegmentsWith { s ->
                                s.splitToSequence(Regex("\\s+|_+"))
                                        .asIterable()
                            }
                        }
                        .followConvention {
                            forEachSegment {
                                forLeadingCharacters {
                                    stripAny { c: Char -> c.isWhitespace() }
                                    replaceEveryFirstCharacter { c: Char -> c.uppercaseChar() }
                                }
                                forAnyCharacter {
                                    transformByWindow {
                                        anyCharacter { c: Char -> c.isUpperCase() }.followedBy { c: Char -> c.isLowerCase() }
                                                .transformInto { c: Char -> " $c" }
                                    }
                                }
                            }
                            furtherSegmentAnyWith(' ')
                            joinSegmentsWithoutAnyDelimiter()
                        }
                        .named("PascalCase"));

    override val conventionName: String
        get() = namingConvention.conventionName
    override val conventionKey: Any
        get() = this

    override fun deriveName(input: String): ConventionalName {
        return namingConvention.deriveName(input)
    }

}