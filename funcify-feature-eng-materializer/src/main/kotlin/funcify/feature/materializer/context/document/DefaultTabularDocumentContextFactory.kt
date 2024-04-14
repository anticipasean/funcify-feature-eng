package funcify.feature.materializer.context.document

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import funcify.feature.materializer.context.document.TabularDocumentContext.Builder
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Document
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * @author smccarron
 * @created 2022-10-23
 */
internal class DefaultTabularDocumentContextFactory : TabularDocumentContextFactory {

    companion object {

        internal class DefaultTabularDocumentContextBuilder(
            private val existingContext: DefaultTabularDocumentContext? = null,
            private val dataElementPathsByExpectedOutputFieldName:
                PersistentMap.Builder<String, GQLOperationPath> =
                existingContext?.dataElementPathsByExpectedOutputFieldName?.builder()
                    ?: persistentMapOf<String, GQLOperationPath>().builder(),
            private val featurePathByExpectedOutputFieldName:
                PersistentMap.Builder<String, GQLOperationPath> =
                existingContext?.featurePathByExpectedOutputFieldName?.builder()
                    ?: persistentMapOf<String, GQLOperationPath>().builder(),
            private val passThruExpectedOutputFieldNames: PersistentSet.Builder<String> =
                existingContext?.passThruExpectedOutputFieldNames?.builder()
                    ?: persistentSetOf<String>().builder(),
            private var document: Option<Document> = existingContext?.document ?: none()
        ) : Builder {

            override fun document(document: Document): Builder =
                this.apply { this.document = document.some() }

            override fun putDataElementPathForFieldName(
                expectedOutputFieldName: String,
                dataElementPath: GQLOperationPath
            ): Builder =
                this.apply {
                    this.dataElementPathsByExpectedOutputFieldName.put(
                        expectedOutputFieldName,
                        dataElementPath
                    )
                }

            override fun putAllDataElementPathsForFieldName(
                dataElementPathsByExpectedOutputFieldNames: Map<String, GQLOperationPath>
            ): Builder =
                this.apply {
                    this.dataElementPathsByExpectedOutputFieldName.putAll(
                        dataElementPathsByExpectedOutputFieldNames
                    )
                }

            override fun putFeaturePathForFieldName(
                expectedOutputFieldName: String,
                featurePath: GQLOperationPath
            ): Builder =
                this.apply {
                    this.featurePathByExpectedOutputFieldName.put(
                        expectedOutputFieldName,
                        featurePath
                    )
                }

            override fun putAllFeaturePathsForFieldNames(
                featurePathsByExpectedOutputFieldNames: Map<String, GQLOperationPath>
            ): Builder =
                this.apply {
                    this.featurePathByExpectedOutputFieldName.putAll(
                        featurePathsByExpectedOutputFieldNames
                    )
                }

            override fun addPassThruExpectedOutputFieldName(
                passThruExpectedFieldName: String
            ): Builder =
                this.apply { this.passThruExpectedOutputFieldNames.add(passThruExpectedFieldName) }

            override fun addAllPassThruExpectedOutputFieldNames(
                passThruExpectedFieldNames: Iterable<String>
            ): Builder =
                this.apply {
                    this.passThruExpectedOutputFieldNames.addAll(passThruExpectedFieldNames)
                }

            override fun build(): TabularDocumentContext {
                return DefaultTabularDocumentContext(
                    dataElementPathsByExpectedOutputFieldName =
                        this.dataElementPathsByExpectedOutputFieldName.build(),
                    featurePathByExpectedOutputFieldName =
                        this.featurePathByExpectedOutputFieldName.build(),
                    passThruExpectedOutputFieldNames =
                        this.passThruExpectedOutputFieldNames.build(),
                    document = this.document
                )
            }
        }

        internal class DefaultTabularDocumentContext(
            override val dataElementPathsByExpectedOutputFieldName:
                PersistentMap<String, GQLOperationPath>,
            override val featurePathByExpectedOutputFieldName:
                PersistentMap<String, GQLOperationPath>,
            override val passThruExpectedOutputFieldNames: PersistentSet<String>,
            override val document: Option<Document>
        ) : TabularDocumentContext {

            override fun update(transformer: Builder.() -> Builder): TabularDocumentContext {
                return transformer(DefaultTabularDocumentContextBuilder(this)).build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultTabularDocumentContextBuilder()
    }
}
