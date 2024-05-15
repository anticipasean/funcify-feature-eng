package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.some
import funcify.feature.schema.path.result.ElementSegment
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import graphql.execution.ResultPath
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2023-10-21
 */
class GQLResultPathTest {

    companion object {
        private fun String.fromDecodedFormToPath(): GQLResultPath {
            val firstColonIndex: Int = this.indexOf(":/")
            return when {
                firstColonIndex < 0 -> {
                    throw IllegalArgumentException(
                        "input does not contain scheme delimiter ':' followed by path start '/'"
                    )
                }
                else -> {
                    GQLResultPath.parseOrThrow(
                        this.substring(firstColonIndex + 2)
                            .splitToSequence("/")
                            .map { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8) }
                            .joinToString("/", this.subSequence(0, firstColonIndex + 2))
                    )
                }
            }
        }
    }

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
                "gqlr:/employees/pets/dogs[0/breed".fromDecodedFormToPath()
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
        Assertions.assertTrue(iae2.message?.contains("after end bracket") ?: false) {
            "message does not have expected content [ actual: throwable.message: %s ]"
                .format(iae2.message)
        }
        val iae3: IllegalArgumentException =
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                "gqlr:/employees/pets/[]/breed".fromDecodedFormToPath()
            }
        Assertions.assertTrue(iae3.message?.contains("not parseable as") ?: false) {
            "message does not have expected content [ actual: throwable.message: %s ]"
                .format(iae3.message)
        }
        val p: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> {
                "gqlr:/employees/pets/dogs[0]/breed".fromDecodedFormToPath()
            }
        Assertions.assertTrue(
            p.elementSegments
                .lastOrNone()
                .filter { es: ElementSegment -> es is NameSegment }
                .isDefined()
        ) {
            "last segment is not %s".format(NameSegment::class.simpleName)
        }
        Assertions.assertTrue(
            p.getParentPath()
                .map(GQLResultPath::elementSegments)
                .flatMap(List<ElementSegment>::lastOrNone)
                .filterIsInstance<ListSegment>()
                .isDefined()
        ) {
            "last segment on parent is not %s".format(ListSegment::class.simpleName)
        }
    }

    /* @Test
        fun elementSegmentsOrderTest() {
            Assertions.assertTrue(NameSegment("dogs") < NameSegment("snakes")) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(NameSegment("dogs") > NameSegment("cats")) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(NameSegment("dogs") == NameSegment("dogs")) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(ListSegment("dogs", 1) > ListSegment("dogs", 0)) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(ListSegment("dogs", 1) < ListSegment("snakes", 0)) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(ListSegment("dogs", 0) == ListSegment("dogs", 0)) {
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
            Assertions.assertTrue(UnnamedListSegment(1) < ListSegment("dogs", 1)) {
                "unexpected comparison result"
            }
            Assertions.assertTrue(NameSegment("dogs") < ListSegment("dogs", 1)) {
                "unexpected comparison result"
            }
        }
    */
    @Test
    fun nativePathConversionTest1() {
        val p: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> {
                "gqlr:/employees/pets/dogs[0]/breed".fromDecodedFormToPath()
            }
        val rp: ResultPath =
            sequenceOf(
                    "employees" to none(),
                    "pets" to none(),
                    "dogs" to 0.some(),
                    "breed" to none()
                )
                .fold(ResultPath.rootPath()) { rp: ResultPath, (n: String, iOpt: Option<Int>) ->
                    when {
                        iOpt.isDefined() -> {
                            rp.segment(n).segment(iOpt.orNull()!!)
                        }
                        else -> {
                            rp.segment(n)
                        }
                    }
                }
        Assertions.assertEquals(
            p.elementSegments.asSequence().joinToString("/", "/"),
            rp.toString()
        ) {
            "expected schema path does not match actual native path representation"
        }
        val calculatedPath: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> { GQLResultPath.fromNativeResultPath(rp) }
        Assertions.assertEquals(p, calculatedPath) {
            "calculated/transformed native path does not match expected path"
        }
    }

    @Test
    fun nativePathConversionTest2() {
        val p: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> {
                "gqlr:/[1]/pets/dogs[0]/breed".fromDecodedFormToPath()
            }
        val rp: ResultPath =
            sequenceOf("" to 1.some(), "pets" to none(), "dogs" to 0.some(), "breed" to none())
                .fold(ResultPath.rootPath()) { rp: ResultPath, (n: String, iOpt: Option<Int>) ->
                    when {
                        n.isNotBlank() && iOpt.isDefined() -> {
                            rp.segment(n).segment(iOpt.orNull()!!)
                        }
                        n.isNotBlank() && !iOpt.isDefined() -> {
                            rp.segment(n)
                        }
                        else -> {
                            rp.segment(iOpt.orNull()!!)
                        }
                    }
                }
        // Note: The native result path does not have a starting '/' when first node represents an
        // unnamed list segment
        Assertions.assertEquals(p.elementSegments.asSequence().joinToString("/"), rp.toString()) {
            "expected schema path does not match actual native path representation"
        }
        val calculatedPath: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> { GQLResultPath.fromNativeResultPath(rp) }
        Assertions.assertEquals(p, calculatedPath) {
            "calculated/transformed native path does not match expected path"
        }
    }

    @Test
    fun nativePathConversionTest3() {
        val p: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> {
                "gqlr:/[1]/pets/dogs[0][1]/breed".fromDecodedFormToPath()
            }
        val rp: ResultPath =
            sequenceOf(
                    "" to listOf(1).some(),
                    "pets" to none(),
                    "dogs" to listOf(0, 1).some(),
                    "breed" to none()
                )
                .fold(ResultPath.rootPath()) { rp: ResultPath, (n: String, iOpt: Option<List<Int>>)
                    ->
                    when {
                        n.isNotBlank() && iOpt.isDefined() -> {
                            iOpt.fold(::emptySequence, List<Int>::asSequence).fold(rp.segment(n)) {
                                r,
                                i ->
                                r.segment(i)
                            }
                        }
                        n.isNotBlank() && !iOpt.isDefined() -> {
                            rp.segment(n)
                        }
                        else -> {
                            iOpt.fold(::emptySequence, List<Int>::asSequence).fold(rp) { r, i ->
                                r.segment(i)
                            }
                        }
                    }
                }
        // Note: The native result path does not have a starting '/' when first node represents an
        // unnamed list segment
        Assertions.assertEquals(p.elementSegments.asSequence().joinToString("/"), rp.toString()) {
            "expected schema path does not match actual native path representation"
        }
        val calculatedPath: GQLResultPath =
            Assertions.assertDoesNotThrow<GQLResultPath> { GQLResultPath.fromNativeResultPath(rp) }
        Assertions.assertEquals(p, calculatedPath) {
            "calculated/transformed native path does not match expected path"
        }
    }
}
