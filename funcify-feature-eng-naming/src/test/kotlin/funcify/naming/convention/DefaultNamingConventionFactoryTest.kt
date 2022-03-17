package funcify.naming.convention

import org.junit.jupiter.api.Test


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
internal class DefaultNamingConventionFactoryTest {

    @Test
    fun createExampleNamingConventionTest() {
        val stringIterable: Iterable<String> = Iterable<String> { sequenceOf("blah").iterator() }
        DefaultNamingConventionFactory().fromInputType(stringIterable::class)
                .extractOneOrMoreStrings { i -> i }
                .splitWith(' ')
                .andTransform {
                    stripAnyLeadingOrTailingCharacters { c: Char -> c == ' ' }
                    replaceAnyCharacterIf({ c -> c == '_' },
                                          { c -> " " })
                    replaceAnyCharacterIf({ c -> c == 'A' },
                                          { c -> "B" })
                    transformAny { c -> c.isUpperCase() }.followedBy { c -> c.isLowerCase() }
                            .into { c -> Character.toLowerCase(c) }
                }.deriveName(stringIterable)
    }


}