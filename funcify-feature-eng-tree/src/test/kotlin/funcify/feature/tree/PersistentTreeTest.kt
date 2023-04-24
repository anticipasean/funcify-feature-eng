package funcify.feature.tree

import arrow.core.filterIsInstance
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.tree.path.TreePath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 2023-04-23
 */
class PersistentTreeTest {
    companion object {
        /**
         * Example taken from
         * [https://hackersandslackers.com/extract-data-from-complex-json-python/]
         */
        val JSON_EXAMPLE: String =
            """
            |{
            |  "destination_addresses": [
            |    "Washington, DC, USA",
            |    "Philadelphia, PA, USA",
            |    "Santa Barbara, CA, USA",
            |    "Miami, FL, USA",
            |    "Austin, TX, USA",
            |    "Napa County, CA, USA"
            |  ],
            |  "origin_addresses": [
            |    "New York, NY, USA"
            |  ],
            |  "rows": [{
            |    "elements": [{
            |        "distance": {
            |          "text": "227 mi",
            |          "value": 365468
            |        },
            |        "duration": {
            |          "text": "3 hours 54 mins",
            |          "value": 14064
            |        },
            |        "status": "OK"
            |      },
            |      {
            |        "distance": {
            |          "text": "94.6 mi",
            |          "value": 152193
            |        },
            |        "duration": {
            |          "text": "1 hour 44 mins",
            |          "value": 6227
            |        },
            |        "status": "OK"
            |      },
            |      {
            |        "distance": {
            |          "text": "2,878 mi",
            |          "value": 4632197
            |        },
            |        "duration": {
            |          "text": "1 day 18 hours",
            |          "value": 151772
            |        },
            |        "status": "OK"
            |      },
            |      {
            |        "distance": {
            |          "text": "1,286 mi",
            |          "value": 2069031
            |        },
            |        "duration": {
            |          "text": "18 hours 43 mins",
            |          "value": 67405
            |        },
            |        "status": "OK"
            |      },
            |      {
            |        "distance": {
            |          "text": "1,742 mi",
            |          "value": 2802972
            |        },
            |        "duration": {
            |          "text": "1 day 2 hours",
            |          "value": 93070
            |        },
            |        "status": "OK"
            |      },
            |      {
            |        "distance": {
            |          "text": "2,871 mi",
            |          "value": 4620514
            |        },
            |        "duration": {
            |          "text": "1 day 18 hours",
            |          "value": 152913
            |        },
            |        "status": "OK"
            |      }
            |    ]
            |  }],
            |  "status": "OK"
            |}
            """.trimMargin()
    }

    @Test
    fun convertJSONIntoPersistentTreeTest() {
        val jsonNode: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> { ObjectMapper().readTree(JSON_EXAMPLE) }
        val persistentTree: PersistentTree<JsonNode> =
            Assertions.assertDoesNotThrow<PersistentTree<JsonNode>> {
                PersistentTree.fromSequenceFunctionOnValue(jsonNode) { jn: JsonNode ->
                    when (jn.nodeType) {
                        JsonNodeType.ARRAY -> {
                            jn.toOption().filterIsInstance<ArrayNode>().fold(::emptySequence) {
                                an: ArrayNode ->
                                an.asSequence().map { j -> j.left() }
                            }
                        }
                        JsonNodeType.OBJECT -> {
                            jn.toOption().filterIsInstance<ObjectNode>().fold(::emptySequence) {
                                on: ObjectNode ->
                                on.fields().asSequence().map { e -> (e.key to e.value).right() }
                            }
                        }
                        else -> {
                            emptySequence()
                        }
                    }
                }
            }
        Assertions.assertInstanceOf(ObjectBranch::class.java, persistentTree)
        // persistentTree.asSequence().forEach { (tp: TreePath, jn: JsonNode) ->
        //    println("path: ${tp}, node: ${jn}")
        // }
        val tp: TreePath =
            Assertions.assertDoesNotThrow<TreePath> {
                TreePath.parseTreePath("tp:/rows/0/elements/5/duration/text")
            }
        Assertions.assertEquals(
            "1 day 18 hours",
            persistentTree[tp]
                .mapNotNull { t -> t.value().orNull() }
                .filterIsInstance<TextNode>()
                .map { t -> t.asText() }
                .orNull()
                ?: ""
        )
    }
}
