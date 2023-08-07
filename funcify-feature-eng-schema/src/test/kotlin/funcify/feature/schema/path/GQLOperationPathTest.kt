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

    /*
    @Test
    fun argumentDisplayTest() {
        val path1: SchematicPath = SchematicPath.getRootPath()
        val path2: SchematicPath =
            path1.transform { pathSegment("pets").pathSegment("dogs").argument("alias", "canines") }
        Assertions.assertEquals(
            SchematicPath.GRAPHQL_SCHEMATIC_PATH_SCHEME +
                ":/pets/dogs?alias=${URICompatibleStringEncoder.invoke("canines")}",
            path2.toString()
        )
    }

    @Test
    fun parentPathSourceIndexTest() {
        val path1: SchematicPath =
            SchematicPath.getRootPath().transform { pathSegment("pets", "dogs") }
        val path2: SchematicPath = SchematicPath.getRootPath().transform { pathSegment("pets") }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }

    @Test
    fun parentPathParameterToSourceIndexTest() {
        val path1: SchematicPath =
            SchematicPath.getRootPath().transform { pathSegment("pets", "dogs").argument("id") }
        val path2: SchematicPath =
            SchematicPath.getRootPath().transform { pathSegment("pets", "dogs") }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }

    @Test
    fun parentPathParameterJunctionIndexToParameterAttributeArgumentTest() {
        val path1: SchematicPath =
            SchematicPath.getRootPath().transform {
                pathSegment("pets", "dogs")
                    .argument(
                        "breed_spec",
                        JsonNodeFactory.instance.objectNode().put("size", "small")
                    )
            }
        val path2: SchematicPath =
            SchematicPath.getRootPath().transform {
                pathSegment("pets", "dogs")
                    .argument("breed_spec", JsonNodeFactory.instance.objectNode())
            }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }

    @Test
    fun parentPathParameterJunctionIndexToParameterAttributeDirectiveTest() {
        val path1: SchematicPath =
            SchematicPath.of {
                pathSegment("pets", "dogs")
                    .directive(
                        "breed_spec",
                        JsonNodeFactory.instance.objectNode().put("size", "small")
                    )
            }
        val path2: SchematicPath =
            SchematicPath.of {
                pathSegment("pets", "dogs")
                    .directive("breed_spec", JsonNodeFactory.instance.objectNode())
            }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }

    @Test
    fun parentPathParameterJunctionIndexToParameterAttributeArgumentsAndDirectivesTest() {
        val path1: SchematicPath =
            SchematicPath.of {
                pathSegment("pets", "dogs")
                    .argument(
                        "breed_spec",
                        JsonNodeFactory.instance.objectNode().put("size", "small")
                    )
                    .directive(
                        "format",
                        JsonNodeFactory.instance
                            .objectNode()
                            .put("template", "<name>: <breed_name>")
                    )
            }
        val path2: SchematicPath =
            SchematicPath.of {
                pathSegment("pets", "dogs")
                    .argument(
                        "breed_spec",
                        JsonNodeFactory.instance.objectNode().put("size", "small")
                    )
                    .directive("format", JsonNodeFactory.instance.objectNode())
            }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }

    @Test
    fun parentPathForNestedParameterLeafIndexTest() {
        val textNodeMaker: (String) -> TextNode = JsonNodeFactory.instance::textNode
        val objectNodeMaker: (Map<String, JsonNode>) -> ObjectNode = { m ->
            m.asSequence().fold(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                on.set(k, v)
            }
        }
        val addressFormatParams =
            mapOf<String, JsonNode>(
                "street_format" to
                    textNodeMaker.invoke(
                        "<street_number> <street_name> <suite_number> <apt_number>"
                    ),
                "city_state_format" to
                    objectNodeMaker.invoke(
                        mapOf(
                            "city_format" to textNodeMaker.invoke("<city>, "),
                            "state_format" to textNodeMaker.invoke("<state_initials>")
                        )
                    ),
                "zip_code_format" to textNodeMaker.invoke("\\d\\d\\d\\d\\d")
            )
        val childPath =
            SchematicPath.of {
                pathSegment("my_db", "my_table_1", "user", "addresses")
                    .argument("format", objectNodeMaker.invoke(addressFormatParams))
            }
        val parentPath = childPath.getParentPath()
        Assertions.assertEquals(
            listOf<String>("city_state_format", "city_format")
                .fold(childPath.arguments["format"] ?: objectNodeMaker.invoke(mapOf())) { jn, path
                    ->
                    jn.path(path)
                }
                .asText(""),
            "<city>, ",
            "child_path.argument[\"format\"] child path doesn't match expected text"
        )
        Assertions.assertNotEquals(childPath, parentPath.orNull())
        Assertions.assertNotNull(parentPath.orNull())
        Assertions.assertTrue(
            parentPath
                .filter { p ->
                    p.arguments.containsKey("format") && p.arguments["format"]!!.isEmpty
                }
                .isDefined(),
            "parent_path expected to have format argument with empty object"
        )
    }*/
}
