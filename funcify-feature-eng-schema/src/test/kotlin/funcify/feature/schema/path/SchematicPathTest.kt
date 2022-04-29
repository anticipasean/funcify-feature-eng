package funcify.feature.schema.path

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class SchematicPathTest {

    @Test
    fun argumentDisplayTest() {
        val path1: SchematicPath = SchematicPath.getRootPath()
        val path2: SchematicPath =
            path1.transform { pathSegment("pets").pathSegment("dogs").argument("alias", "canines") }
        Assertions.assertEquals("fes:/pets/dogs?alias=canines", path2.toString())
    }
}
