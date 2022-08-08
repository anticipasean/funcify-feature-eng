package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestFieldMaterializationGraphService :
    SingleRequestFieldMaterializationGraphService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestFieldMaterializationGraphService>()
    }

    override fun createMaterializationGraphForSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession> {
        logger.debug(
            "create_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return Deferred.completed(session)
    }
}
