package funcify.feature.materializer.context.document

import arrow.core.Option
import arrow.core.none
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.context.document.ColumnarDocumentContext.Builder
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Document
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

/**
 * @author smccarron
 * @created 2022-10-23
 */
internal class DefaultColumnarDocumentContextFactory : ColumnarDocumentContextFactory {

    companion object {

        internal class DefaultColumnarDocumentContextBuilder(
            private var expectedFieldNames: PersistentList.Builder<String> =
                persistentListOf<String>().builder(),
            private var parameterValuesByPath: PersistentMap.Builder<GQLOperationPath, JsonNode> =
                persistentMapOf<GQLOperationPath, JsonNode>().builder(),
            private var sourceIndexPathsByFieldName:
                PersistentMap.Builder<String, GQLOperationPath> =
                persistentMapOf<String, GQLOperationPath>().builder(),
            private var queryComposerFunction:
                Option<(ImmutableMap<GQLOperationPath, JsonNode>) -> OperationDefinition> =
                none(),
            private var operationDefinition: Option<OperationDefinition> = none(),
            private var document: Option<Document> = none()
        ) : Builder {

            override fun expectedFieldNames(expectedFieldNames: List<String>): Builder {
                when (expectedFieldNames) {
                    is PersistentList -> {
                        this.expectedFieldNames = expectedFieldNames.builder()
                    }
                    else -> {
                        this.expectedFieldNames = expectedFieldNames.toPersistentList().builder()
                    }
                }
                return this
            }

            override fun parameterValuesByPath(
                parameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>
            ): Builder {
                this.parameterValuesByPath = parameterValuesByPath.builder()
                return this
            }

            override fun addParameterValueForPath(
                path: GQLOperationPath,
                jsonValue: JsonNode
            ): Builder {
                this.parameterValuesByPath[path] = jsonValue
                return this
            }

            override fun removeParameterValueWithPath(path: GQLOperationPath): Builder {
                this.parameterValuesByPath.remove(path)
                return this
            }

            override fun sourceIndexPathsByFieldName(
                sourceIndexPathsByFieldName: PersistentMap<String, GQLOperationPath>
            ): Builder {
                this.sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.builder()
                return this
            }

            override fun addSourceIndexPathForFieldName(
                fieldName: String,
                path: GQLOperationPath
            ): Builder {
                this.sourceIndexPathsByFieldName[fieldName] = path
                return this
            }

            override fun queryComposerFunction(
                function: (ImmutableMap<GQLOperationPath, JsonNode>) -> OperationDefinition
            ): Builder {
                this.queryComposerFunction = Option(function)
                return this
            }

            override fun operationDefinition(operationDefinition: OperationDefinition): Builder {
                this.operationDefinition = Option(operationDefinition)
                return this
            }

            override fun document(document: Document): Builder {
                this.document = Option(document)
                return this
            }

            override fun build(): ColumnarDocumentContext {
                return DefaultColumnarDocumentContext(
                    expectedFieldNames = expectedFieldNames.build(),
                    parameterValuesByPath = parameterValuesByPath.build(),
                    sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.build(),
                    queryComposerFunction = queryComposerFunction,
                    operationDefinition = operationDefinition,
                    document = document
                )
            }
        }

        internal class DefaultColumnarDocumentContext(
            override val expectedFieldNames: PersistentList<String> = persistentListOf(),
            override val parameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode> =
                persistentMapOf(),
            override val sourceIndexPathsByFieldName: PersistentMap<String, GQLOperationPath> =
                persistentMapOf(),
            override val queryComposerFunction:
                Option<(ImmutableMap<GQLOperationPath, JsonNode>) -> OperationDefinition> =
                none(),
            override val operationDefinition: Option<OperationDefinition> = none(),
            override val document: Option<Document> = none()
        ) : ColumnarDocumentContext {

            override fun update(transformer: Builder.() -> Builder): ColumnarDocumentContext {
                return transformer(
                        DefaultColumnarDocumentContextBuilder(
                            expectedFieldNames = expectedFieldNames.builder(),
                            parameterValuesByPath = parameterValuesByPath.builder(),
                            sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.builder(),
                            queryComposerFunction = queryComposerFunction,
                            operationDefinition = operationDefinition,
                            document = document
                        )
                    )
                    .build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultColumnarDocumentContextBuilder()
    }
}
