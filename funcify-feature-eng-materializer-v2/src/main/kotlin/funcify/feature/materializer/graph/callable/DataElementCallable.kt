package funcify.feature.materializer.graph.callable

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface DataElementCallable : (ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<JsonNode> {

    val domainPath: GQLOperationPath

    val expectedArgumentPaths: ImmutableSet<GQLOperationPath>
}
