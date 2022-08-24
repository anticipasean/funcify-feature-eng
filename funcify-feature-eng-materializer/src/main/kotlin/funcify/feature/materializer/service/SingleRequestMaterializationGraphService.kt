package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
interface SingleRequestMaterializationGraphService :
    MaterializationGraphService<SingleRequestFieldMaterializationSession> {

    override fun createRequestMaterializationGraphForSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession>
}
