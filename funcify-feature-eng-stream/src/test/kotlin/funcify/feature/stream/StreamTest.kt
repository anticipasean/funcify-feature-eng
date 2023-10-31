package funcify.feature.stream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import java.io.FileReader
import java.util.stream.Stream
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder

/**
 * @author smccarron
 * @created 2023-06-23
 */
class StreamTest {

    companion object {
        private val NETFLIX_SHOWS_PATH: String =
            "netflix_movies_and_tv_shows_202306091725/netflix_titles.csv"
    }

    @Test
    fun streamFunctionTest() {
        val csvFile: File =
            Assertions.assertDoesNotThrow<File> { ClassPathResource(NETFLIX_SHOWS_PATH).file }
        val firstCsvRowJson: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> {
                CSVParser.parse(FileReader(csvFile), CSVFormat.DEFAULT)
                    .use { p: CSVParser ->
                        var header: Map<String, Int> = mapOf()
                        p.stream()
                            .flatMap { c: CSVRecord ->
                                if (c.recordNumber == 1L) {
                                    header =
                                        c.values().asSequence().withIndex().associate { (idx, k) ->
                                            k to idx
                                        }
                                    Stream.empty<JsonNode>()
                                } else {
                                    Stream.of(
                                        header.entries.asSequence().fold(
                                            JsonNodeFactory.instance.objectNode()
                                        ) { rowJson: ObjectNode, (k: String, idx: Int) ->
                                            rowJson.put(k, c.get(idx))
                                        }
                                    )
                                }
                            }
                            .findFirst()
                    }
                    .orElseThrow()
            }
        SpringApplicationBuilder(
                *TestChannelBinderConfiguration.getCompleteConfiguration(
                    StreamFunctions::class.java
                )
            )
            .run(
                "--spring.cloud.function.definition=materializeFeatures",
                "--spring.cloud.stream.bindings.materializeFeatures-in-0.destination=myInput",
                "--spring.cloud.stream.bindings.materializeFeatures-out-0.destination=myOutput"
            )
            .use { context: ConfigurableApplicationContext ->
                val inputDestination: InputDestination =
                    context.getBean(InputDestination::class.java)
                val outputDestination: OutputDestination =
                    context.getBean(OutputDestination::class.java)
                val objectMapper: ObjectMapper = ObjectMapper()
                val inputMessage: Message<ByteArray?> =
                    Assertions.assertDoesNotThrow<Message<ByteArray?>> {
                        MessageBuilder.withPayload(objectMapper.writeValueAsBytes(firstCsvRowJson))
                            .build()
                    }
                inputDestination.send(inputMessage, "myInput")
                var outputMessage: Message<ByteArray?> = outputDestination.receive(0, "myOutput")
                val resultNode: JsonNode =
                    Assertions.assertDoesNotThrow<JsonNode> {
                        objectMapper.readTree(outputMessage.payload)
                    }
                Assertions.assertEquals(JsonNodeFactory.instance.textNode("Hello"), resultNode) {
                    "actual_result_node: %s"
                        .format(
                            ObjectMapper()
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(resultNode)
                        )
                }
            }
    }
}
