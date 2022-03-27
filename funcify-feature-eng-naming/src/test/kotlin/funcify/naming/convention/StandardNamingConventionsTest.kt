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
        Assertions.assertEquals("", conventionalName.qualifiedForm)
    }

}