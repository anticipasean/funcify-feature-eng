package funcify.feature.materializer.service

import funcify.feature.materializer.session.FeatureMaterializationSession
import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationOrchestratorService {

    fun materializeDataElementsInSession(materializationSession: FeatureMaterializationSession): Async<FeatureMaterializationSession>

}