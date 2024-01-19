package funcify.feature.materializer.response.factory

import funcify.feature.materializer.response.SerializedGraphQLResponse

/**
 * @author smccarron
 * @created 2022-08-05
 */
interface SerializedGraphQLResponseFactory {

    fun builder(): SerializedGraphQLResponse.Builder
}
