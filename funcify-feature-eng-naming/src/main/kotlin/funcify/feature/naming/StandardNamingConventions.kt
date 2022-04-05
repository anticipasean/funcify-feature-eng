package funcify.feature.naming

import funcify.feature.naming.convention.DefaultNamingConventionFactory


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
                           forEverySegment {
                               forLeadingCharacters {
                                   stripAnyLeadingWhitespace()
                               }
                               forAnyCharacter {
                                   transformCharactersByWindow {
                                       anyUppercaseCharacter().precededByALowercaseLetter()
                                               .transformInto { c: Char -> "_$c" }
                                   }
                                   transformCharactersByWindow {
                                       anyDigit().precededByALowercaseLetter()
                                               .transformInto { c: Char -> "_$c" }
                                   }
                                   makeAllLowercase()
                               }
                           }
                           splitAnySegmentsWith('_')
                       }
                       .joinSegmentsWith('_')
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
                           forFirstSegment {
                               makeLeadingCharacterOfFirstSegmentLowercase()
                           }
                           forEverySegment {
                               forAnyCharacter {
                                   transformCharactersByWindow {
                                       anyUppercaseCharacter().followedByALowercaseLetter()
                                               .transformInto { c: Char -> "_$c" }
                                   }
                               }
                           }
                           splitAnySegmentsWith('_')
                           forEverySegment {
                               forLeadingCharacters {
                                   stripAnyLeadingWhitespace()
                                   replaceLeadingCharactersOfOtherSegmentsIf({ c: Char -> c.isLowerCase() },
                                                                             { c: Char -> c.uppercase() })
                               }
                           }

                       }
                       .joinSegmentsWithoutDelimiter()
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
                            forEverySegment {
                                forLeadingCharacters {
                                    stripAnyLeadingWhitespace()
                                    makeEachLeadingCharacterUppercase()
                                }
                                forAnyCharacter {
                                    transformCharactersByWindow {
                                        anyUppercaseCharacter().followedByALowercaseLetter()
                                                .transformInto { c: Char -> " $c" }
                                    }
                                }
                            }
                            splitAnySegmentsWith(' ')
                        }
                        .joinSegmentsWithoutDelimiter()
                        .named("PascalCase"));

    override val conventionName: String
        get() = namingConvention.conventionName
    override val conventionKey: Any
        get() = this

    override fun deriveName(input: String): ConventionalName {
        return namingConvention.deriveName(input)
    }

}