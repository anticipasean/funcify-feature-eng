package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleRequestMaterializationPreprocessingService :
    MaterializationRequestPreprocessingService<SingleRequestFieldMaterializationSession> {

    override fun preprocessRequestMaterializationGraphInSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession>
}
