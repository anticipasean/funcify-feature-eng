package funcify.naming.convention

import funcify.naming.NameSegment
import funcify.naming.NamingConvention
import funcify.naming.StandardNamingConventions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName


/**
 *
 * @author smccarron
 * @created 3/27/22
 */
class StandardNamingConventionsTest {

    @Test
    fun basicSnakeCaseTest() {
        val conventionalName = StandardNamingConventions.SNAKE_CASE.deriveName(StandardNamingConventionsTest::class.qualifiedName
                                                                               ?: "")
        Assertions.assertEquals("funcify.naming.convention.standard_naming_conventions_test",
                                conventionalName.qualifiedForm)
    }

    @Test
    fun basicCamelCaseTest() {
        val conventionalName = StandardNamingConventions.CAMEL_CASE.deriveName(StandardNamingConventionsTest::class.simpleName
                                                                               ?: "")
        Assertions.assertEquals(StandardNamingConventionsTest::class.simpleName?.replaceFirstChar { c: Char -> c.lowercaseChar() },
                                conventionalName.qualifiedForm)
        val segmentsAsStrings = (StandardNamingConventionsTest::class.simpleName?.splitToSequence(Regex("(?<=[a-z])\\B(?=[A-Z])"))
                                         ?.toList()
                                 ?: emptyList()).let { strs: List<String> ->
            if (strs.isNotEmpty() && strs[0].first()
                            .isUpperCase()) {
                sequenceOf(strs[0].replace(strs[0].first(),
                                           strs[0].first()
                                                   .lowercaseChar())).plus(strs.asSequence()
                                                                                   .drop(1))
                        .toList()
            } else {
                strs
            }
        }
        val resultSegmentsAsStrings = conventionalName.nameSegments.map(NameSegment::value)
                .toList()
        Assertions.assertEquals(segmentsAsStrings,
                                resultSegmentsAsStrings)
    }

    @Test
    fun basicPascalCaseTest() {
        val conventionalName = StandardNamingConventions.PASCAL_CASE.deriveName(StandardNamingConventionsTest::class.simpleName
                                                                                ?: "")
        Assertions.assertEquals(StandardNamingConventionsTest::class.simpleName,
                                conventionalName.qualifiedForm)
        val segmentsAsStrings = StandardNamingConventionsTest::class.simpleName?.splitToSequence(Regex("(?<=[a-z])\\B(?=[A-Z])"))
                                        ?.toList()
                                ?: emptyList()
        val resultSegmentsAsStrings = conventionalName.nameSegments.map(NameSegment::value)
                .toList()
        Assertions.assertEquals(segmentsAsStrings,
                                resultSegmentsAsStrings)
    }

    @Test
    fun createSnakeCaseConventionForKClassTest() {
        val kClassNamingConvention: NamingConvention<KClass<*>> = DefaultNamingConventionFactory.getInstance()
                .createConventionFrom(StandardNamingConventions.SNAKE_CASE)
                .mapping<KClass<*>> { kc ->
                    kc.qualifiedName?.replace(Regex("^[\\w.]+\\."),
                                              "")
                    ?: kc.jvmName
                }
                .named("KClassSnakeCase")
        Assertions.assertEquals("standard_naming_conventions",
                                kClassNamingConvention.deriveName(StandardNamingConventions::class).qualifiedForm)
    }

    @Test
    fun createCamelCaseConventionForStringTest() {
        val kClassNamingConvention: NamingConvention<KClass<*>> = DefaultNamingConventionFactory.getInstance()
                .createConventionFrom(StandardNamingConventions.CAMEL_CASE)
                .mapping<KClass<*>> { kc ->
                    kc.qualifiedName?.replace(Regex("^[\\w.]+\\."),
                                              "")
                    ?: kc.jvmName
                }
                .named("KClassCamelCase")
        Assertions.assertEquals("standardNamingConventions",
                                kClassNamingConvention.deriveName(StandardNamingConventions::class).qualifiedForm)
    }

    @Test
    fun createPascalCaseConventionForStringTest() {
        val kClassNamingConvention: NamingConvention<KClass<*>> = DefaultNamingConventionFactory.getInstance()
                .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
                .mapping<KClass<*>> { kc ->
                    kc.qualifiedName?.replace(Regex("^[\\w.]+\\."),
                                              "")
                    ?: kc.jvmName
                }
                .named("KClassPascalCase")
        Assertions.assertEquals("StandardNamingConventions",
                                kClassNamingConvention.deriveName(StandardNamingConventions::class).qualifiedForm)
    }

}