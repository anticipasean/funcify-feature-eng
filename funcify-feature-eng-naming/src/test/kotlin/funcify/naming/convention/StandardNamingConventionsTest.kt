package funcify.naming.convention

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
        val conventionalName = StandardNamingConventions.CAMEL_CASE.deriveName(StandardNamingConventionsTest::class.qualifiedName
                                                                               ?: "")
        Assertions.assertEquals(StandardNamingConventionsTest::class.qualifiedName,
                                conventionalName.qualifiedForm)
    }

    @Test
    fun createForKClassTest() {
        val kClassNamingConvention: NamingConvention<KClass<*>> = DefaultNamingConventionFactory.getInstance()
                .createConventionFor<KClass<*>>()
                .whenInputProvided {
                    treatAsOneSegment { kc: KClass<*> ->
                        kc.qualifiedName
                        ?: kc.jvmName
                    }
                }
                .followConvention {
                    forEachSegment {
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
                .named("KClassSnakeCase")
        println(kClassNamingConvention.deriveName(StandardNamingConventions::class))
    }

}