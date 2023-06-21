package funcify.feature.stream

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.messaging.Message
import reactor.core.publisher.Flux

/**
 * @author smccarron
 * @created 2023-06-20
 */
class StreamFunctions {

    fun materializeFeatures(): (Flux<Message<Any?>>) -> Flux<Message<JsonNode>> {
        return { messagePublisher: Flux<Message<Any?>> ->
             messagePublisher.map { m: Message<Any?> ->
                 m.headers
             }
            Flux.empty()
        }
    }
}
