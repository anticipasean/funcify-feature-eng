package funcify.feature.stream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-20
 */
@SpringBootApplication
class StreamFunctions {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StreamFunctions::class.java)
        private val objectMapper: ObjectMapper = ObjectMapper()

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(StreamFunctions::class.java, *args)
        }
    }

    @Bean
    fun materializeFeatures(): (Flux<Message<Any?>>) -> Flux<Message<JsonNode>> {
        return { messagePublisher: Flux<Message<Any?>> ->
            messagePublisher.flatMap { m: Message<Any?> ->
                logger.info("headers: {}", m.headers)
                when (val pl: Any? = m.payload) {
                    null -> {
                        // TODO: Instate null node treatment here: Mono.just(nullNode)
                        // or Mono.empty<JsonNode>()
                        Mono.just(JsonNodeFactory.instance.nullNode())
                    }
                    is ByteArray -> {
                        Mono.fromSupplier {
                            try {
                                // TODO: Instate null node treatment here: Mono.just(nullNode)
                                // or Mono.empty<JsonNode>()
                                objectMapper.readTree(pl) ?: JsonNodeFactory.instance.nullNode()
                            } catch (t: Throwable) {
                                JsonNodeFactory.instance
                                    .objectNode()
                                    .put("errorType", t::class.qualifiedName)
                                    .put("errorMessage", t.message)
                            }
                        }
                    }
                    is String -> {
                        Mono.just(JsonNodeFactory.instance.textNode(pl))
                    }
                    is JsonNode -> {
                        Mono.just(pl)
                    }
                    else -> {
                        Mono.fromSupplier {
                            val supportedPayloadTypes: String =
                                sequenceOf(
                                        ByteArray::class.qualifiedName,
                                        String::class.qualifiedName,
                                        JsonNode::class.qualifiedName
                                    )
                                    .joinToString(", ", "[ ", " ]")
                            val message: String =
                                """unsupported message payload type: 
                                    |[ expected: one of %s, actual: %s ]"""
                                    .trimMargin()
                                    .replace(System.lineSeparator(), "")
                                    .format(supportedPayloadTypes, pl::class.qualifiedName)
                            JsonNodeFactory.instance
                                .objectNode()
                                .put("errorType", IllegalArgumentException::class.qualifiedName)
                                .put("errorMessage", message)
                        }
                    }
                }.map { jn: JsonNode -> GenericMessage<JsonNode>(jn, m.headers) }
            }
        }
    }
}
