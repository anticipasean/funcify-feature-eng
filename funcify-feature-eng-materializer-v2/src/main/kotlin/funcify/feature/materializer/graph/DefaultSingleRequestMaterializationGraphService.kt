package funcify.feature.materializer.graph

import arrow.core.Option
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.language.Document
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestMaterializationGraphService :
    SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationGraphService>()
        private sealed interface RequestGraphCacheKey
        private data class RawInputContextCompatibleKey(
            private val rawInputContextKeysSet: PersistentSet<String>,
            private val document: Document
        ) : RequestGraphCacheKey
        private data class StandardKey(private val document: Document) : RequestGraphCacheKey

        private fun interface RequestMaterializationGraphFunction {

            fun getRequestMaterializationGraph(
                rawInputContext: Option<RawInputContext>,
                processedVariables: ImmutableMap<String, Any?>,
                document: Document
            ): RequestMaterializationGraph
        }
    }

    //    private val requestMaterializationGraphCache: ConcurrentMap<>

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.debug(
            "create_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return Mono.just(session)
    }
}
