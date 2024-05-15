package funcify.feature.schema.json

import arrow.core.getOrElse
import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class JsonNodeValueExtractionByOperationPathTest {

    companion object {
        private fun String.toJsonNode(): JsonNode {
            return try {
                ObjectMapper().readTree(this)
            } catch (t: Throwable) {
                Assertions.fail<JsonNode>(t)
            }
        }

        @JvmStatic
        @BeforeAll
        internal fun setUp() {
            (LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.let {
                lc: ch.qos.logback.classic.LoggerContext ->
                lc.getLogger(JsonNodeValueExtractionByOperationPath::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    @Test
    fun extractRootTest() {
        val jn: JsonNode =
            """
            |[
            |  123,
            |  124,
            |  125
            |]
            """
                .flatten()
                .toJsonNode()
        val p1: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> { GQLOperationPath.getRootPath() }
        val result1: JsonNode =
            JsonNodeValueExtractionByOperationPath.invoke(jn, p1).getOrElse {
                JsonNodeFactory.instance.nullNode()
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, result1)
        Assertions.assertEquals(
            sequenceOf(123, 124, 125).fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add),
            result1
        ) {
            "result does not match expected sequence"
        }
    }

    @Test
    fun extractListValueTest() {
        val jn: JsonNode =
            """
            |{
            |  "dogs": [
            |    {
            |      "id": 123,
            |      "name": "Bob"
            |    },
            |    {
            |      "id": 124,
            |      "name": "Joey"
            |    },
            |    {
            |      "id": 125,
            |      "name": "Phoebe"
            |    }
            |  ]
            |}
            """
                .flatten()
                .toJsonNode()
        val p1: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/dogs/id")
            }
        val result1: JsonNode =
            JsonNodeValueExtractionByOperationPath.invoke(jn, p1).getOrElse {
                JsonNodeFactory.instance.nullNode()
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, result1)
        Assertions.assertEquals(
            sequenceOf(123, 124, 125).fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add),
            result1
        ) {
            "result does not match expected sequence"
        }

        val p2: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/dogs/name")
            }
        val result2: JsonNode =
            JsonNodeValueExtractionByOperationPath.invoke(jn, p2).getOrElse {
                JsonNodeFactory.instance.nullNode()
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, result2)
        Assertions.assertEquals(
            sequenceOf("Bob", "Joey", "Phoebe")
                .fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add),
            result2
        ) {
            "result does not match expected sequence"
        }
    }

    @Test
    fun extractNestedListValueTest() {
        val jn: JsonNode =
            """
            |{
            |  "dogs": [
            |    {
            |      "id": 123,
            |      "name": "Bob",
            |      "breed": {
            |        "id": 321
            |      }
            |    },
            |    {
            |      "id": 124,
            |      "name": "Joey",
            |      "breed": {
            |        "id": 322
            |      }
            |    },
            |    {
            |      "id": 125,
            |      "name": "Phoebe",
            |      "breed": {
            |        "id": 322
            |      }
            |    }
            |  ]
            |}
            """
                .flatten()
                .toJsonNode()
        val p1: GQLOperationPath =
            Assertions.assertDoesNotThrow<GQLOperationPath> {
                GQLOperationPath.parseOrThrow("gqlo:/dogs/breed/id")
            }
        val result1: JsonNode =
            JsonNodeValueExtractionByOperationPath.invoke(jn, p1).getOrElse {
                JsonNodeFactory.instance.nullNode()
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, result1)
        Assertions.assertEquals(
            sequenceOf(321, 322, 322).fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add),
            result1
        ) {
            "result does not match expected sequence"
        }
    }
}
