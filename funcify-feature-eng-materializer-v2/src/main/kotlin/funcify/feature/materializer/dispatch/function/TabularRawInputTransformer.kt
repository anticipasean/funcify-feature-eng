package funcify.feature.materializer.dispatch.function

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.dispatch.context.DispatchInputContext.TabularRawInput
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-09
 */
fun interface TabularRawInputTransformer : (TabularRawInput) -> Mono<JsonNode> {}
