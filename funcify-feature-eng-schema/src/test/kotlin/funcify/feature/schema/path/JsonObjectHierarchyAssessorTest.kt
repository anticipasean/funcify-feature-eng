package funcify.feature.schema.path

import arrow.core.foldLeft
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2022-07-28
 */
class JsonObjectHierarchyAssessorTest {

    private val textNodeCreator: (text: String) -> TextNode = JsonNodeFactory.instance::textNode

    private val objectNodeCreator: (jsonObjectMap: Map<String, JsonNode>) -> ObjectNode =
        { m: Map<String, JsonNode> ->
            m.foldLeft(JsonNodeFactory.instance.objectNode()) { objectNode, (k, v) ->
                objectNode.set<ObjectNode>(k, v)
            }
        }

    private val doubleNodeCreator: (Double) -> NumericNode = JsonNodeFactory.instance::numberNode

    @Test
    fun parentChildRelationshipTestWithDifferentiatorParentBeingFirstNode() {
        val parent =
            persistentMapOf<String, JsonNode>("person" to objectNodeCreator.invoke(mapOf()))
        val child =
            parent.put(
                "person",
                objectNodeCreator.invoke(mapOf("name" to textNodeCreator.invoke("Bob")))
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.PARENT_CHILD,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(parent, child)
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.CHILD_PARENT,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(child, parent)
        )
    }

    @Test
    fun parentChildRelationshipTestWithDifferentiatorParentNotBeingFirstNode() {
        val parent =
            persistentMapOf<String, JsonNode>(
                "vehicle" to
                    objectNodeCreator.invoke(
                        mapOf(
                            "make" to textNodeCreator.invoke("Honda"),
                            "model" to textNodeCreator.invoke("Civic"),
                            "year" to textNodeCreator.invoke("1998")
                        )
                    ),
                "person" to objectNodeCreator.invoke(mapOf())
            )
        val child =
            parent.put(
                "person",
                objectNodeCreator.invoke(mapOf("name" to textNodeCreator.invoke("Bob")))
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.PARENT_CHILD,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(parent, child)
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.CHILD_PARENT,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(child, parent)
        )
    }

    @Test
    fun siblingsRelationshipTestWithFirstNode() {
        val sibling1 =
            persistentMapOf<String, JsonNode>(
                "person" to objectNodeCreator.invoke(mapOf("name" to textNodeCreator.invoke("Bob")))
            )
        val sibling2 =
            sibling1.put(
                "person",
                objectNodeCreator.invoke(
                    mapOf("address" to textNodeCreator.invoke("123 SomeTree Street"))
                )
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.SIBLING_SIBLING,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                sibling1,
                sibling2
            )
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.SIBLING_SIBLING,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                sibling2,
                sibling1
            )
        )
    }

    @Test
    fun ancestorDescendentRelationshipTestWithDifferentiatorAncestorBeingFirstNode() {
        val ancestor =
            persistentMapOf<String, JsonNode>("person" to objectNodeCreator.invoke(mapOf()))
        val descendent =
            ancestor.put(
                "person",
                objectNodeCreator.invoke(
                    mapOf(
                        "addresses" to
                            objectNodeCreator.invoke(
                                mapOf(
                                    "home_address" to
                                        objectNodeCreator.invoke(
                                            mapOf(
                                                "street_address" to
                                                    textNodeCreator.invoke("123 SomeTree Street"),
                                                "city" to textNodeCreator.invoke("New York"),
                                                "state" to textNodeCreator.invoke("NY")
                                            )
                                        )
                                )
                            )
                    )
                )
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.ANCESTOR_DESCENDENT,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                ancestor,
                descendent
            )
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.DESCENDENT_ANCESTOR,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                descendent,
                ancestor
            )
        )
    }

    @Test
    fun ancestorDescendentRelationshipTestWithDifferentiatorAncestorNotBeingFirstNode() {
        val ancestor =
            persistentMapOf<String, JsonNode>(
                "vehicle" to
                    objectNodeCreator.invoke(
                        mapOf(
                            "make" to textNodeCreator.invoke("Honda"),
                            "model" to textNodeCreator.invoke("Civic"),
                            "year" to textNodeCreator.invoke("1998")
                        )
                    ),
                "person" to objectNodeCreator.invoke(mapOf())
            )
        val descendent =
            ancestor.put(
                "person",
                objectNodeCreator.invoke(
                    mapOf(
                        "addresses" to
                            objectNodeCreator.invoke(
                                mapOf(
                                    "home_address" to
                                        objectNodeCreator.invoke(
                                            mapOf(
                                                "street_address" to
                                                    textNodeCreator.invoke("123 SomeTree Street"),
                                                "city" to textNodeCreator.invoke("New York"),
                                                "state" to textNodeCreator.invoke("NY")
                                            )
                                        )
                                )
                            )
                    )
                )
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.ANCESTOR_DESCENDENT,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                ancestor,
                descendent
            )
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.DESCENDENT_ANCESTOR,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                descendent,
                ancestor
            )
        )
    }

    @Test
    fun unrelatedAtNestedLevelTest() {
        val lineage1 =
            persistentMapOf<String, JsonNode>(
                "vehicle" to
                    objectNodeCreator.invoke(
                        mapOf(
                            "make" to textNodeCreator.invoke("Honda"),
                            "model" to textNodeCreator.invoke("Civic"),
                            "year" to textNodeCreator.invoke("1998")
                        )
                    ),
                "person" to
                    objectNodeCreator.invoke(
                        mapOf(
                            "name" to textNodeCreator.invoke("Bob"),
                            "addresses" to
                                objectNodeCreator.invoke(
                                    mapOf(
                                        "home_address" to
                                            objectNodeCreator.invoke(
                                                mapOf(
                                                    "street_address" to
                                                        textNodeCreator.invoke(
                                                            "123 SomeTree Street"
                                                        ),
                                                    "city" to textNodeCreator.invoke("New York"),
                                                    "state" to textNodeCreator.invoke("NY")
                                                )
                                            )
                                    )
                                )
                        )
                    )
            )
        val lineage2 =
            lineage1.put(
                "person",
                objectNodeCreator.invoke(
                    mapOf(
                        "occupation" to textNodeCreator.invoke("plumber"),
                        "addresses" to
                            objectNodeCreator.invoke(
                                mapOf(
                                    "home_address" to
                                        objectNodeCreator.invoke(
                                            mapOf(
                                                "street_address" to
                                                    textNodeCreator.invoke(
                                                        "123 DifferentTree Street"
                                                    ),
                                                "city" to textNodeCreator.invoke("New York"),
                                                "state" to textNodeCreator.invoke("NY")
                                            )
                                        )
                                )
                            )
                    )
                )
            )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.NOT_RELATED,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                lineage1,
                lineage2
            )
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.NOT_RELATED,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                lineage2,
                lineage1
            )
        )
    }

    @Test
    fun unrelatedAtRootLevelTest() {
        val lineage1 =
            mapOf<String, JsonNode>(
                "vehicle" to
                    objectNodeCreator.invoke(
                        mapOf(
                            "make" to textNodeCreator.invoke("Honda"),
                            "model" to textNodeCreator.invoke("Civic"),
                            "year" to textNodeCreator.invoke("1998")
                        )
                    ),
                "person" to objectNodeCreator.invoke(mapOf("name" to textNodeCreator.invoke("Bob")))
            )
        val lineage2 = mapOf<String, JsonNode>("vehicle" to objectNodeCreator.invoke(mapOf()))
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.NOT_RELATED,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                lineage1,
                lineage2
            )
        )
        Assertions.assertEquals(
            JsonObjectHierarchyAssessor.RelationshipType.NOT_RELATED,
            JsonObjectHierarchyAssessor.findRelationshipTypeBetweenTwoJsonObjectMaps(
                lineage2,
                lineage1
            )
        )
    }
}
