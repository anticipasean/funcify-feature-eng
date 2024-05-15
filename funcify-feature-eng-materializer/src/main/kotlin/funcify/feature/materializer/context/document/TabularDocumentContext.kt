package funcify.feature.materializer.context.document

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Document
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2022-10-22
 */
interface TabularDocumentContext {

    companion object {

        val TABULAR_DOCUMENT_CONTEXT_KEY: String =
            TabularDocumentContext::class.qualifiedName + ".TABULAR_DOCUMENT_CONTEXT"
    }

    val dataElementPathsByExpectedOutputFieldName: ImmutableMap<String, GQLOperationPath>

    val featurePathByExpectedOutputFieldName: ImmutableMap<String, GQLOperationPath>

    val passThruExpectedOutputFieldNames: ImmutableSet<String>

    val document: Option<Document>

    fun update(transformer: Builder.() -> Builder): TabularDocumentContext

    interface Builder {

        fun document(document: Document): Builder

        fun putDataElementPathForFieldName(
            expectedOutputFieldName: String,
            dataElementPath: GQLOperationPath
        ): Builder

        fun putAllDataElementPathsForFieldName(
            dataElementPathsByExpectedOutputFieldNames: Map<String, GQLOperationPath>
        ): Builder

        fun putFeaturePathForFieldName(
            expectedOutputFieldName: String,
            featurePath: GQLOperationPath
        ): Builder

        fun putAllFeaturePathsForFieldNames(
            featurePathsByExpectedOutputFieldNames: Map<String, GQLOperationPath>
        ): Builder

        fun addPassThruExpectedOutputFieldName(passThruExpectedFieldName: String): Builder

        fun addAllPassThruExpectedOutputFieldNames(
            passThruExpectedFieldNames: Iterable<String>
        ): Builder

        fun build(): TabularDocumentContext
    }
}
