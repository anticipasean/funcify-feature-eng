package funcify.feature.schema.json

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.schema.path.result.GQLResultPath
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GQLResultValuesToJsonNodeConverterTest {

    companion object {
        private const val NOT_AVAILABLE = "NA"

        private fun String.fromDecodedFormToPath(): GQLResultPath {
            val firstColonIndex: Int = this.indexOf(":/")
            return when {
                firstColonIndex < 0 -> {
                    Assertions.fail<GQLResultPath>(
                        "input does not contain scheme delimiter ':' followed by path start '/'"
                    )
                }
                else -> {
                    Assertions.assertDoesNotThrow<GQLResultPath>({
                        GQLResultPath.parseOrThrow(
                            this.substring(firstColonIndex + 2)
                                .splitToSequence("/")
                                .map { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8) }
                                .joinToString("/", this.subSequence(0, firstColonIndex + 2))
                        )
                    }) {
                        "could not create path with [ string: %s ]".format(this)
                    }
                }
            }
        }

        @JvmStatic
        @BeforeAll
        internal fun setUp() {
            (LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.let {
                lc: ch.qos.logback.classic.LoggerContext ->
                lc.getLogger(GQLResultValuesToJsonNodeConverter::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    @Test
    fun createListNodeValueTest() {
        val m: Map<GQLResultPath, JsonNode> =
            sequenceOf<Pair<GQLResultPath, JsonNode>>(
                    "gqlr:/user/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Charlie"),
                    "gqlr:/user/pets/dogs[0]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Bob"),
                    "gqlr:/user/pets/dogs[0]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Labrador Retriever"),
                    "gqlr:/user/pets/dogs[0]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(true),
                    "gqlr:/user/pets/dogs[1]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Karen"),
                    "gqlr:/user/pets/dogs[1]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Boston Terrier"),
                    "gqlr:/user/pets/dogs[1]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(false),
                    "gqlr:/app/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Simple")
                )
                .fold(persistentMapOf<GQLResultPath, JsonNode>()) { pm, (rp, jn) -> pm.put(rp, jn) }
        val jn: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode>({
                GQLResultValuesToJsonNodeConverter.invoke(m)
            }) {
                "unable to create instance of [ %s ] from result map"
                    .format(JsonNode::class.qualifiedName)
            }
        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jn))
        Assertions.assertEquals("Charlie", jn.path("user").path("name").asText(NOT_AVAILABLE))
        Assertions.assertEquals(
            "Karen",
            jn.path("user").path("pets").path("dogs").path(1).path("name").asText(NOT_AVAILABLE)
        )
        Assertions.assertFalse(
            jn.path("user").path("pets").path("dogs").path(1).path("chipped").asBoolean(true)
        )
    }

    @Test
    fun createNestedListNodeValueTest() {
        val m: Map<GQLResultPath, JsonNode> =
            sequenceOf<Pair<GQLResultPath, JsonNode>>(
                    "gqlr:/user/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Charlie"),
                    "gqlr:/user/pets/dogs[0][0]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Bob"),
                    "gqlr:/user/pets/dogs[0][0]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Labrador Retriever"),
                    "gqlr:/user/pets/dogs[0][0]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(true),
                    "gqlr:/user/pets/dogs[0][1]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Karen"),
                    "gqlr:/user/pets/dogs[0][1]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Boston Terrier"),
                    "gqlr:/user/pets/dogs[0][1]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(false),
                    "gqlr:/user/pets/dogs[1][0]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Joey"),
                    "gqlr:/user/pets/dogs[1][0]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Border Collie"),
                    "gqlr:/user/pets/dogs[1][0]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(true),
                    "gqlr:/user/pets/dogs[1][1]/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Terra"),
                    "gqlr:/user/pets/dogs[1][1]/breed".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Golden Retriever"),
                    "gqlr:/user/pets/dogs[1][1]/chipped".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.booleanNode(false),
                    "gqlr:/app/name".fromDecodedFormToPath() to
                        JsonNodeFactory.instance.textNode("Simple")
                )
                .fold(persistentMapOf<GQLResultPath, JsonNode>()) { pm, (rp, jn) -> pm.put(rp, jn) }
        val jn: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode>({
                GQLResultValuesToJsonNodeConverter.invoke(m)
            }) {
                "unable to create instance of [ %s ] from result map"
                    .format(JsonNode::class.qualifiedName)
            }
        println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jn))
        Assertions.assertEquals(
            "Bob",
            jn.path("user")
                .path("pets")
                .path("dogs")
                .path(0)
                .path(0)
                .path("name")
                .asText(NOT_AVAILABLE)
        )
        Assertions.assertEquals(
            "Karen",
            jn.path("user")
                .path("pets")
                .path("dogs")
                .path(0)
                .path(1)
                .path("name")
                .asText(NOT_AVAILABLE)
        )
        Assertions.assertEquals(
            "Joey",
            jn.path("user")
                .path("pets")
                .path("dogs")
                .path(1)
                .path(0)
                .path("name")
                .asText(NOT_AVAILABLE)
        )
        Assertions.assertEquals(
            "Terra",
            jn.path("user")
                .path("pets")
                .path("dogs")
                .path(1)
                .path(1)
                .path("name")
                .asText(NOT_AVAILABLE)
        )
        Assertions.assertEquals(
            "Border Collie",
            jn.path("user")
                .path("pets")
                .path("dogs")
                .path(1)
                .path(0)
                .path("breed")
                .asText(NOT_AVAILABLE)
        )
        Assertions.assertEquals("Charlie", jn.path("user").path("name").asText(NOT_AVAILABLE))
        Assertions.assertEquals("Simple", jn.path("app").path("name").asText(NOT_AVAILABLE))
    }
}
