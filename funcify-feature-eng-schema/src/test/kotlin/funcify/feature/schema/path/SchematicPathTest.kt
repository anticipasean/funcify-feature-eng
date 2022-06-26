package funcify.feature.schema.path

import arrow.core.some
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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
                ":/pets/dogs?alias=${URICompatibleStringEncoder.invoke("\"canines\"")}",
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
                    .argument("names", JsonNodeFactory.instance.arrayNode().add("Fido"))
            }
        val path2: SchematicPath =
            SchematicPath.getRootPath().transform { pathSegment("pets", "dogs").argument("names") }
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
            SchematicPath.getRootPath().transform {
                pathSegment("pets", "dogs")
                    .directive("names", JsonNodeFactory.instance.arrayNode().add("Fido"))
            }
        val path2: SchematicPath =
            SchematicPath.getRootPath().transform { pathSegment("pets", "dogs").directive("names") }
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
            SchematicPath.getRootPath().transform {
                pathSegment("pets", "dogs")
                    .argument("id", 123)
                    .directive("names", JsonNodeFactory.instance.arrayNode().add("Fido"))
            }
        val path2: SchematicPath =
            SchematicPath.getRootPath().transform {
                pathSegment("pets", "dogs").argument("id", 123).directive("names")
            }
        Assertions.assertTrue(path2.isParentTo(path1), "path2 is not a parent to path1")
        Assertions.assertTrue(path1.isChildTo(path2), "path1 is not a child of path2")
        Assertions.assertEquals(
            path2.some(),
            path1.getParentPath(),
            "path1 is expected to be the same as the parent of path2"
        )
    }
}
