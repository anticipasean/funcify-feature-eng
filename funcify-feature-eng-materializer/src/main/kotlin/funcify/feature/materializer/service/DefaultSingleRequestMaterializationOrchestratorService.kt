package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.none
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationOrchestratorService :
    SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<Pair<SingleRequestFieldMaterializationSession, Deferred<Option<Any>>>> {
        logger.info("materialize_value_in_session: [ session.session_id: ${session.sessionId} ]")
        logger.info("field: {}", session.dataFetchingEnvironment.field)
        return Try.success(session to Deferred.completed(none()))
    }
}
