package funcify.feature.materializer.gql

/**
 * @author smccarron
 * @created 2023-10-12
 */
interface GQLDocumentSpecFactory {

    companion object {

        fun defaultFactory(): GQLDocumentSpecFactory {
            return DefaultGQLDocumentSpecFactory()
        }
    }

    fun builder(): GQLDocumentSpec.Builder
}
