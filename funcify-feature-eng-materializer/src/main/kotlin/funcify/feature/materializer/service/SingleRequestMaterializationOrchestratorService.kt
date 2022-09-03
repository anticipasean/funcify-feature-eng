package funcify.feature.materializer.service

import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-29
 */
interface SingleRequestMaterializationOrchestratorService :
    MaterializationOrchestratorService<SingleRequestFieldMaterializationSession> {

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any>
}
