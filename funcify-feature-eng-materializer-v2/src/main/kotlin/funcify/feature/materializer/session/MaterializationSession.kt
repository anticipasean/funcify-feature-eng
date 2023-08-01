package funcify.feature.materializer.session

import funcify.feature.materializer.schema.MaterializationMetamodel
import java.util.*

/**
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationSession {

    /**
     * In a session for a single request, the session identifier is the same as the request
     * identifier.
     *
     * In a session for multiple requests e.g. websocket setup, the session identifier would not be
     * the same as any of the constituent request identifiers
     */
    val sessionId: UUID

    val materializationMetamodel: MaterializationMetamodel
}
