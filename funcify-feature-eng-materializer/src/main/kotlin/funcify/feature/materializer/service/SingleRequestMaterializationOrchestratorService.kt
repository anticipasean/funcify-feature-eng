package funcify.feature.materializer.service

import arrow.core.Option
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.async.KFuture

/**
 *
 * @author smccarron
 * @created 2022-08-29
 */
interface SingleRequestMaterializationOrchestratorService :
    MaterializationOrchestratorService<SingleRequestFieldMaterializationSession> {

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<Pair<SingleRequestFieldMaterializationSession, KFuture<Option<Any>>>>
}
