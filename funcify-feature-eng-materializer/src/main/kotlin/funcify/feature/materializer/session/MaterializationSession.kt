package funcify.feature.materializer.session

import graphql.schema.GraphQLSchema
import java.util.*

/**
 *
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

    /**
     * Instance of the schema to be used during the course of request processing for the given
     * session. This instance may be different from session to session if there has been an update
     * requiring changes be made to it
     */
    val materializationSchema: GraphQLSchema
}
