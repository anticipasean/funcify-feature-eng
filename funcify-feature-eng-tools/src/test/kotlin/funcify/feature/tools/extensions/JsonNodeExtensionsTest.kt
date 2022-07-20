package funcify.feature.tools.extensions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.JsonNodeExtensions.addChildFieldAndValuePairToRightmostTreeNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 2022-07-20
 */
class JsonNodeExtensionsTest {

    companion object {

        val JSON_SNIPPET: String =
            """
            |{
            |  "info": {
            |    "title": "My Endpoint",
            |    "description": "My Endpoint - My Functions",
            |    "version": "0.1.1"
            |  },
            |  "paths": {
            |    "/some_path/some_calculated_attribute": {
            |      "description": "My Calculated Attribute",
            |      "post": {
            |        "requestBody": {
            |          "content": {
            |            "application/json": {
            |              "schema": {
            |                "type": "object",
            |                "properties": {
            |                  "input_field_1": {
            |                    "description": "My input field 1",
            |                    "type": "number"
            |                  },
            |                  "input_field_2": {
            |                    "description": "My input field 2",
            |                    "type": "number"
            |                  },
            |                  "input_field_3": {
            |                    "description": "My input field 3",
            |                    "type": "number"
            |                  }
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  }
            |}
            """.trimMargin()

        val JSON_SNIPPET_AFTER_UPDATE: String =
            """
            |{
            |  "info" : {
            |    "title" : "My Endpoint",
            |    "description" : "My Endpoint - My Functions",
            |    "version" : "0.1.1"
            |  },
            |  "paths" : {
            |    "/some_path/some_calculated_attribute" : {
            |      "description" : "My Calculated Attribute",
            |      "post" : {
            |        "requestBody" : {
            |          "content" : {
            |            "application/json" : {
            |              "schema" : {
            |                "type" : "object",
            |                "properties" : {
            |                  "input_field_1" : {
            |                    "description" : "My input field 1",
            |                    "type" : "number"
            |                  },
            |                  "input_field_2" : {
            |                    "description" : "My input field 2",
            |                    "type" : "number"
            |                  },
            |                  "input_field_3" : {
            |                    "description" : "My input_field_3 description has been updated",
            |                    "type" : "number"
            |                  }
            |                }
            |              }
            |            }
            |          }
            |        }
            |      }
            |    }
            |  }
            |}
            """.trimMargin()
    }

    @Test
    fun addFieldNameAndValuePairToRightmostChildNodeTest() {
        val jsonNodeAttempt: Try<JsonNode> = Try.attempt { ObjectMapper().readTree(JSON_SNIPPET) }
        if (jsonNodeAttempt.isFailure()) {
            Assertions.fail<Unit>(jsonNodeAttempt.getFailure().orNull()!!)
        }
        val jsonNode: JsonNode = jsonNodeAttempt.orElseThrow()
        val childNodePair: Pair<String, JsonNode> =
            "description" to
                JsonNodeFactory.instance.textNode("My input_field_3 description has been updated")
        val updatedJsonNodeResult: JsonNode =
            jsonNode
                .addChildFieldAndValuePairToRightmostTreeNode(
                    childNodePair.first,
                    childNodePair.second
                )
                .orNull()!!

        val expectedUpdatedJsonNodeAttempt =
            Try.attempt { ObjectMapper().readTree(JSON_SNIPPET_AFTER_UPDATE) }
        if (expectedUpdatedJsonNodeAttempt.isFailure()) {
            Assertions.fail<Unit>(expectedUpdatedJsonNodeAttempt.getFailure().orNull()!!)
        }
        val expectedUpdatedJsonNode = expectedUpdatedJsonNodeAttempt.orNull()!!
        Assertions.assertEquals(
            expectedUpdatedJsonNode,
            updatedJsonNodeResult,
            "expected and actual json_nodes do not match"
        )
    }

    @Test
    fun addFieldNameAndValuePairToRightmostNullChildNodeTest() {
        val jsonNodeAttempt: Try<JsonNode> = Try.attempt { ObjectMapper().readTree(JSON_SNIPPET) }
        if (jsonNodeAttempt.isFailure()) {
            Assertions.fail<Unit>(jsonNodeAttempt.getFailure().orNull()!!)
        }
        val newPath = "/some_path/my_latest_attribute"
        val jsonNode: JsonNode =
            when (val directNode: JsonNode = jsonNodeAttempt.orElseThrow()) {
                is ObjectNode -> {
                    directNode.set(
                        "paths",
                        (directNode.get("paths") as ObjectNode).putNull(newPath)
                    )
                }
                else -> directNode
            }
        val attributeDescription = "My latest attribute description"
        val updatedNode =
            jsonNode
                .addChildFieldAndValuePairToRightmostTreeNode(
                    "description",
                    JsonNodeFactory.instance.textNode(attributeDescription)
                )
                .orNull()!!
        Assertions.assertEquals(
            updatedNode.path("paths").path(newPath).path("description").asText(""),
            attributeDescription,
            "expected and actual new child 'description' nodes do not match"
        )
    }
}
