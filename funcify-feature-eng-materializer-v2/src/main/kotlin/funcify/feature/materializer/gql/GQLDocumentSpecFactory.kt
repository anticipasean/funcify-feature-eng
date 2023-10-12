package funcify.feature.materializer.gql

/**
 *
 * @author smccarron
 * @created 2023-10-12
 */
interface GQLDocumentSpecFactory {

    fun builder(): GQLDocumentSpec.Builder

}
