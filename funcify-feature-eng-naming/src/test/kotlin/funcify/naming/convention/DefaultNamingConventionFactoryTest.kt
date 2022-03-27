package funcify.naming.convention


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
internal class DefaultNamingConventionFactoryTest {

    //@Test
    // Enable when implementation done
    fun createExampleNamingConventionTest() {
        val stringIterable: Iterable<String> = Iterable<String> { sequenceOf("blah").iterator() }
        DefaultNamingConventionFactory().createConventionFor<Iterable<String>>()
                .whenInputProvided {
                    extractOneOrMoreSegmentsWith { i -> i }
                }
                .followConvention {
                    forEachSegment {
                        forLeadingCharacters {
                            stripAny { c: Char -> c == ' ' }
                        }
                        forAnyCharacter {
                            transformIf({ c -> c == '_' },
                                        { c -> ' ' })
                            transformIf({ c -> c == 'A' },
                                        { c -> 'B' })
                            transformByPosition {
                                anyCharacter { c -> c.isUpperCase() }.followedBy { c -> c.isLowerCase() }
                                        .transformInto { c -> c.lowercase() }
                            }
                        }
                        forTrailingCharacters {
                            stripAny { c: Char -> c == ' ' }
                        }
                    }
                    joinSegmentsWith('_')
                }
                .named("my_str_iterable_convention")
                .deriveName(stringIterable)
    }


}