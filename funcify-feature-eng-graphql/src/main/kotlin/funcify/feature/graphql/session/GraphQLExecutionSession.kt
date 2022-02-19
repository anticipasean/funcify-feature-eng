package funcify.feature.graphql.session

import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.materializer.session.FeatureMaterializationSession


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLExecutionSession : FeatureMaterializationSession {

    val rawGraphQLRequest: RawGraphQLRequest

}