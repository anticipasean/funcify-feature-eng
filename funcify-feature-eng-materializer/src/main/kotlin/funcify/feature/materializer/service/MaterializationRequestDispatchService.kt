package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred

/**
 * Takes the session's request materialization graph with its [RequestParameterEdge]s and packages
 * them into calls to providers of the materialized values
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationRequestDispatchService<M : MaterializationSession> {

    fun dispatchRequestsInMaterializationGraphInSession(session: M): Try<M>
}
