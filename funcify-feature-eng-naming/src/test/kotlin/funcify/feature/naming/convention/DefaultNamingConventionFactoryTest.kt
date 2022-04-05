package funcify.feature.naming.convention

import kotlin.reflect.KClass


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
                    forEverySegment {
                        forLeadingCharacters {
                            stripAnyLeadingCharacters { c: Char -> c == ' ' }
                        }
                        forAnyCharacter {
                            transformAnyCharacterIf({ c -> c == '_' },
                                                    { _ -> ' ' })
                            transformAnyCharacterIf({ c -> c == 'A' },
                                                    { _ -> 'B' })
                            transformCharactersByWindow {
                                anyCharacter { c -> c.isUpperCase() }.followedBy { c -> c.isLowerCase() }
                                        .transformInto { c -> c.lowercase() }
                            }
                        }
                        forTrailingCharacters {
                            stripAnyTrailingCharacters { c: Char -> c == ' ' }
                        }
                    }
                }
                .joinSegmentsWith('_')
                .named("my_str_iterable_convention")
                .deriveName(stringIterable)
    }


}