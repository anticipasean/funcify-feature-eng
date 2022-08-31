package funcify.feature.materializer.session

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.tools.container.async.KFuture

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface GraphQLSingleRequestSessionFactory {

    fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): KFuture<GraphQLSingleRequestSession>
}
