package funcify.feature.schema.transformer

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface TransformerCallable : (ImmutableMap<String, JsonNode>) -> Mono<JsonNode> {

    val transformerPath: GQLOperationPath

    val argumentNames: ImmutableSet<String>
}
