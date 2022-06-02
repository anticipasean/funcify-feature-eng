package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationPostprocessingService {

    fun convertMaterializedValuesIntoExpectedOutputFormat(
        materializationSession: MaterializationSession
    ): Deferred<MaterializationSession>

}
