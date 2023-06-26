package funcify.feature.stream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder

/**
 * @author smccarron
 * @created 2023-06-23
 */
class StreamTest {

    @Test
    fun streamFunctionTest() {
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
                        MessageBuilder.withPayload(
                                objectMapper.writeValueAsBytes(
                                    JsonNodeFactory.instance.textNode("Hello")
                                )
                            )
                            .build()
                    }
                inputDestination.send(inputMessage, "myInput")
                var outputMessage: Message<ByteArray?> = outputDestination.receive(0, "myOutput")
                val resultNode: JsonNode =
                    Assertions.assertDoesNotThrow<JsonNode> {
                        objectMapper.readTree(outputMessage.payload)
                    }
                assertThat(resultNode).isEqualTo(JsonNodeFactory.instance.textNode("Hello"))
            }
    }
}
