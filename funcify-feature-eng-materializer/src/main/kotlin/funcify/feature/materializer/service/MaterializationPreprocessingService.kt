package funcify.feature.materializer.service

import funcify.feature.materializer.session.FeatureMaterializationSession
import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationPreprocessingService {

    fun findMatchingSchematicVerticesForInputValues(featureMaterializationSession: FeatureMaterializationSession): Async<FeatureMaterializationSession>

}