package funcify.feature.schema.path

import arrow.core.some
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.naming.encoder.URICompatibleStringEncoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class SchematicPathTest {

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
    }
}
