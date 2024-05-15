package funcify.feature.materializer.context.document

/**
 * @author smccarron
 * @created 2022-10-23
 */
interface TabularDocumentContextFactory {

    fun builder(): TabularDocumentContext.Builder
}
