package funcify.feature.materializer.request

interface RawGraphQLRequestFactory {

    fun builder(): RawGraphQLRequest.Builder

}
