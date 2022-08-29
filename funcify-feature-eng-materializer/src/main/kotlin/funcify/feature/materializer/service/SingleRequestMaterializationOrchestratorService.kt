package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession

/**
 *
 * @author smccarron
 * @created 2022-08-29
 */
interface SingleRequestMaterializationOrchestratorService :
    MaterializationOrchestratorService<SingleRequestFieldMaterializationSession> {}
