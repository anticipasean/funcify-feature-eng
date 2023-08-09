package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-08-08
 */
interface DispatchedRequestMaterializationGraph {

    val transformerPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>

    val dataElementPublishersByPath: ImmutableMap<GQLOperationPath, Mono<JsonNode>>



    interface Builder {

        fun addTransformerPublisher(path: GQLOperationPath, publisher: Mono<JsonNode>): Builder

        fun addAllTransformerPublishers(publisherPairs: Iterable<Pair<GQLOperationPath, Mono<JsonNode>>>): Builder

        fun addDataElementPublisher(path: GQLOperationPath, publisher: Mono<JsonNode>): Builder

        fun addAllDataElementPublishers(publisherPairs: Iterable<Pair<GQLOperationPath, Mono<JsonNode>>>): Builder

        fun build(): DispatchedRequestMaterializationGraph

    }


}
