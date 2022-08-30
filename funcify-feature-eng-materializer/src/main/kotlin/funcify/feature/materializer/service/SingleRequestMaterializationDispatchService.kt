package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleRequestMaterializationDispatchService :
    MaterializationRequestDispatchService<SingleRequestFieldMaterializationSession> {

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<SingleRequestFieldMaterializationSession>
}
