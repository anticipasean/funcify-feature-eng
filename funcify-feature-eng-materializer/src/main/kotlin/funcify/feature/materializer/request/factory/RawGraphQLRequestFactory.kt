package funcify.feature.materializer.request.factory

import funcify.feature.materializer.request.RawGraphQLRequest

interface RawGraphQLRequestFactory {

    fun builder(): RawGraphQLRequest.Builder
}
