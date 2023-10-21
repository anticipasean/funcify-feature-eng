package funcify.feature.schema.path

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import funcify.feature.schema.path.result.ElementSegment
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.NamedListSegment
import funcify.feature.schema.path.result.NamedSegment
import funcify.feature.schema.path.result.UnnamedListSegment
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2023-10-21
 */
class GQLResultPathTest {

    companion object {}

    @Test
    fun parseTest() {
        val encodeSegments: (String) -> String = { fullPathString: String ->
            val firstColonIndex: Int = fullPathString.indexOf(":/")
            fullPathString
                .substring(firstColonIndex + 2)
                .splitToSequence("/")
                .map { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8) }
                .joinToString("/", fullPathString.subSequence(0, firstColonIndex + 2))
        }
        val iae1: IllegalArgumentException =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                GQLResultPath.parseOrThrow(encodeSegments("gqlr:/employees/pets/dogs[0/breed"))
            }
        Assertions.assertTrue(iae1.message?.contains("end bracket") ?: false) {
            "message does not have expected content [ actual: throwable.message: %s ]"
                .format(iae1.message)
        }
        val iae2: IllegalArgumentException =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                GQLResultPath.parseOrThrow(
                    encodeSegments("gqlr:/employees/pets/dogs[0]something/breed")
                )
            }
        Assertions.assertTrue(iae2.message?.contains("text after") ?: false) {
            "message does not have expected content [ actual: throwable.message: %s ]"
                .format(iae2.message)
        }
        val iae3: IllegalArgumentException =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                GQLResultPath.parseOrThrow(encodeSegments("gqlr:/employees/pets/[]/breed"))
            }
        Assertions.assertTrue(iae3.message?.contains("no index") ?: false) {
            "message does not have expected content [ actual: throwable.message: %s ]"
                .format(iae3.message)
        }
        val p: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> {
                GQLResultPath.parseOrThrow(encodeSegments("gqlr:/employees/pets/dogs[0]/breed"))
            }
        Assertions.assertTrue(
            p.elementSegments
                .lastOrNone()
                .filter { es: ElementSegment -> es is NamedSegment }
                .isDefined()
        ) {
            "last segment is not %s".format(NamedSegment::class.simpleName)
        }
        Assertions.assertTrue(
            p.getParentPath()
                .map(GQLResultPath::elementSegments)
                .flatMap(List<ElementSegment>::lastOrNone)
                .filterIsInstance<NamedListSegment>()
                .isDefined()
        ) {
            "last segment on parent is not %s".format(NamedListSegment::class.simpleName)
        }
    }

    @Test
    fun elementSegmentsOrderTest() {
        Assertions.assertTrue(NamedSegment("dogs") < NamedSegment("snakes")) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedSegment("dogs") > NamedSegment("cats")) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedSegment("dogs") == NamedSegment("dogs")) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedListSegment("dogs", 1) > NamedListSegment("dogs", 0)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedListSegment("dogs", 1) < NamedListSegment("snakes", 0)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedListSegment("dogs", 0) == NamedListSegment("dogs", 0)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(UnnamedListSegment(0) < UnnamedListSegment(1)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(UnnamedListSegment(1) > UnnamedListSegment(0)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(UnnamedListSegment(1) == UnnamedListSegment(1)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(UnnamedListSegment(1) < NamedListSegment("dogs", 1)) {
            "unexpected comparison result"
        }
        Assertions.assertTrue(NamedSegment("dogs") < NamedListSegment("dogs", 1)) {
            "unexpected comparison result"
        }
    }
}
