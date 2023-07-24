package funcify.feature.materializer.response

/**
 *
 * @author smccarron
 * @created 2022-08-05
 */
interface SerializedGraphQLResponseFactory {

    fun builder(): SerializedGraphQLResponse.Builder

}
