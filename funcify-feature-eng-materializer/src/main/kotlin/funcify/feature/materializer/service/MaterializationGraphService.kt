package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationGraphService<M : MaterializationSession> {

    fun createRequestMaterializationGraphForSession(session: M): Try<M>
}
