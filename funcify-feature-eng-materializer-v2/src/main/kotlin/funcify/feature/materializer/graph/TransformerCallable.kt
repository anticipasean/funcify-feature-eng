package funcify.feature.materializer.graph

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-08-01
 */
interface TransformerCallable: (ImmutableMap<String, JsonNode>) -> Mono<JsonNode> {}
