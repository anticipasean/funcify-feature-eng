package funcify.naming.convention

import funcify.naming.StandardNamingConventions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


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

}