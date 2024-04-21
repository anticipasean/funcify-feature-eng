package funcify.feature.schema.path

import arrow.core.compareTo
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GQLOperationPathTest {

    @Test
    fun pathSegmentsOnlyTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath = p1.transform { appendField("pets") }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertEquals(0, p2.getParentPath().orNull()?.selection?.size)
    }

    @Test
    fun fieldsAndInlineFragmentsTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                appendInlineFragment("Dog", "breed")
            }
        val p3: GQLOperationPath =
            p1.transform {
                appendField("pets")
                appendField("name")
            }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(2, p2.selection.size)
        Assertions.assertTrue(p1.compareTo(p2) < 0)
        Assertions.assertTrue(p3.compareTo(p2) < 0)
        Assertions.assertEquals(1, p2.getParentPath().orNull()?.selection?.size)
    }

    @Test
    fun bogusFieldsInlineFragmentsAndFragmentSpreadsTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                appendField("")
                appendInlineFragment("", "breed")
            }
        val p3: GQLOperationPath = p1.transform { appendField("pets") }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertTrue(p2.compareTo(p3) == 0)
        Assertions.assertEquals(p1, p2.getParentPath().orNull())
        Assertions.assertEquals(0, p2.getParentPath().orNull()?.selection?.size)
    }

    @Test
    fun pathSegmentsAndArgumentsTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                argument("breed")
            }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertEquals(1, p2.getParentPath().orNull()?.selection?.size)
        Assertions.assertEquals(p1.transform { field("pets") }, p2.getParentPath().orNull())
    }

    @Test
    fun pathSegmentsArgumentsAndDirectivesTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                argument("breed")
                directive("alias")
            }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertEquals(1, p2.getParentPath().orNull()?.selection?.size)
        Assertions.assertEquals(
            p1.transform {
                field("pets")
                argument("breed")
            },
            p2.getParentPath().orNull()
        )
    }

    @Test
    fun argumentsOnlyTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                argument("breed", "name")
            }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertEquals(1, p2.getParentPath().orNull()?.selection?.size)
        Assertions.assertTrue(
            p2.argument.isDefined() &&
                p2.argument
                    .filter { (_: String, pathSegments: ImmutableList<String>) ->
                        pathSegments.isNotEmpty()
                    }
                    .isDefined()
        )
        Assertions.assertEquals(
            p1.transform {
                field("pets")
                argument("breed")
            },
            p2.getParentPath().orNull()
        )
    }

    @Test
    fun directivesOnlyTest() {
        val p1: GQLOperationPath = GQLOperationPath.getRootPath()
        val p2: GQLOperationPath =
            p1.transform {
                appendField("pets")
                directive("format", "uppercase")
            }
        Assertions.assertNotEquals(0, p2.selection.compareTo(p1.selection))
        Assertions.assertEquals(1, p2.selection.size)
        Assertions.assertEquals(1, p2.getParentPath().orNull()?.selection?.size)
        Assertions.assertTrue(
            p2.argument.isEmpty() &&
                p2.directive
                    .filter { (_: String, pathSegments: ImmutableList<String>) ->
                        pathSegments.isNotEmpty()
                    }
                    .isDefined()
        )
        Assertions.assertEquals(
            p1.transform {
                field("pets")
                directive("format")
            },
            p2.getParentPath().orNull()
        )
    }

    @Test
    fun parseFieldsArgumentsAndDirectivesTest() {
        val sp: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/dogs?breed=/size/small#format=/camelCase")
            }
        Assertions.assertEquals(2, sp.selection.size)
        Assertions.assertEquals(
            persistentListOf("pets", "dogs").asSequence().map(::FieldSegment).toPersistentList(),
            sp.selection
        )
        Assertions.assertEquals("breed", sp.argument.orNull()?.first)
        Assertions.assertEquals(persistentListOf("size", "small"), sp.argument.orNull()?.second)
        Assertions.assertEquals("format", sp.directive.orNull()?.first)
        Assertions.assertEquals(persistentListOf("camelCase"), sp.directive.orNull()?.second)
        val expectedParentPath: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/dogs?breed=/size/small#format")
            }
        Assertions.assertEquals(expectedParentPath, sp.getParentPath().orNull())
    }

    @Test
    fun parseFieldsInlineFragmentsArgumentsAndDirectivesTest() {
        val sp: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow(
                    "gqlo:/pets/%5BDogFragment%3ADog%5Dbreed/origin?format=/initials"
                )
            }
        Assertions.assertEquals(3, sp.selection.size)
        Assertions.assertEquals(
            persistentListOf(
                FieldSegment("pets"),
                FragmentSpreadSegment("DogFragment", "Dog", FieldSegment("breed")),
                FieldSegment("origin")
            ),
            sp.selection
        )
        Assertions.assertEquals("format", sp.argument.orNull()?.first)
        Assertions.assertEquals(persistentListOf("initials"), sp.argument.orNull()?.second)
        val expectedParentPath: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow(
                    "gqlo:/pets/%5BDogFragment%3ADog%5Dbreed/origin?format"
                )
            }
        Assertions.assertEquals(expectedParentPath, sp.getParentPath().orNull())
    }

    @Test
    fun parseBogusFieldsInlineFragmentsArgumentsAndDirectivesTest() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            GQLOperationPath.parseOrThrow("gqlo:/pets/%5BDogFragment%3ADog/origin?format=/initials")
        }
        // contains empty field name for FragmentSpread.fieldName position
        val sp: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/%5BDogFragment%3ADog%5D/origin?format")
            }
        Assertions.assertEquals(2, sp.selection.size)
        Assertions.assertEquals(
            persistentListOf(FieldSegment("pets"), FieldSegment("origin")),
            sp.selection
        )
        Assertions.assertEquals("format", sp.argument.orNull()?.first)
        Assertions.assertEquals(persistentListOf<String>(), sp.argument.orNull()?.second)
        val expectedParentPath: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/origin")
            }
        Assertions.assertEquals(expectedParentPath, sp.getParentPath().orNull())
    }

    @Test
    fun selectionSegmentComparisonTest() {
        Assertions.assertTrue(
            FieldSegment("pets") < InlineFragmentSegment("Dog", FieldSegment("breed"))
        )
        Assertions.assertTrue(
            FragmentSpreadSegment("DogFragment", "Dog", FieldSegment("breed")) >
                InlineFragmentSegment("Dog", FieldSegment("breed"))
        )
        Assertions.assertTrue(FieldSegment("pets") > FieldSegment("dog"))
        Assertions.assertTrue(
            InlineFragmentSegment("Dog", FieldSegment("breed")) <
                InlineFragmentSegment("Dog", FieldSegment("name"))
        )
        Assertions.assertTrue(
            FragmentSpreadSegment("DogFragment", "Dog", FieldSegment("breed")) <
                FragmentSpreadSegment("DogFragment", "Dog", FieldSegment("name"))
        )
        Assertions.assertTrue(
            FragmentSpreadSegment("CatFragment", "Cat", FieldSegment("litterboxLocation")) <
                FragmentSpreadSegment("DogFragment", "Dog", FieldSegment("name"))
        )
        Assertions.assertTrue(
            FragmentSpreadSegment("CatFragment", "Cat", FieldSegment("litterboxLocation")) <
                FragmentSpreadSegment("OtherCatFragment", "Cat", FieldSegment("litterboxLocation"))
        )
    }

    @Test
    fun fullPathComparisonTest() {
        val child: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/dogs/breed/origin")
            }
        val parent: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/pets/dogs/breed")
            }
        Assertions.assertFalse(child == parent) { "child_path should not be equal to parent_path" }
        Assertions.assertTrue(parent < child) { "parent_path should be less than child_path" }
        Assertions.assertTrue(child > parent) { "child_path should be greater than parent_path" }
        Assertions.assertFalse(child < parent) { "child_path should not be less than parent_path" }
    }
}
