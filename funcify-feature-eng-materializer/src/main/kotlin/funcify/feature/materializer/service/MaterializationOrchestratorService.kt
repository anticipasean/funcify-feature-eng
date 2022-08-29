package funcify.feature.materializer.service

import arrow.core.Option
import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationOrchestratorService<M : MaterializationSession> {

    fun materializeValueInSession(session: M): Deferred<Pair<M, Option<Any>>>

}
