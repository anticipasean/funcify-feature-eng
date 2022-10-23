package funcify.feature.materializer.context.document

import arrow.core.Option
import arrow.core.none
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.context.document.ColumnarDocumentContext.Builder
import funcify.feature.schema.path.SchematicPath
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-10-23
 */
internal class DefaultColumnarDocumentContextFactory : ColumnarDocumentContextFactory {

    companion object {

        internal class DefaultColumnarDocumentContextBuilder(
            private var expectedFieldNames: PersistentList.Builder<String> =
                persistentListOf<String>().builder(),
            private var parameterValuesByPath: PersistentMap.Builder<SchematicPath, JsonNode> =
                persistentMapOf<SchematicPath, JsonNode>().builder(),
            private var sourceIndexPathsByFieldName: PersistentMap.Builder<String, SchematicPath> =
                persistentMapOf<String, SchematicPath>().builder(),
            private var queryComposerFunction:
                Option<(ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition> =
                none()
        ) : Builder {

            override fun expectedFieldNames(expectedFieldNames: PersistentList<String>): Builder {
                this.expectedFieldNames = expectedFieldNames.builder()
                return this
            }

            override fun parameterValuesByPath(
                parameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
            ): Builder {
                this.parameterValuesByPath = parameterValuesByPath.builder()
                return this
            }

            override fun addParameterValueForPath(
                path: SchematicPath,
                jsonValue: JsonNode
            ): Builder {
                this.parameterValuesByPath[path] = jsonValue
                return this
            }

            override fun sourceIndexPathsByFieldName(
                sourceIndexPathsByFieldName: PersistentMap<String, SchematicPath>
            ): Builder {
                this.sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.builder()
                return this
            }

            override fun addSourceIndexPathForFieldName(
                fieldName: String,
                path: SchematicPath
            ): Builder {
                this.sourceIndexPathsByFieldName[fieldName] = path
                return this
            }

            override fun queryComposerFunction(
                function: (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition
            ): Builder {
                this.queryComposerFunction = Option(function)
                return this
            }

            override fun build(): ColumnarDocumentContext {
                return DefaultColumnarDocumentContext(
                    expectedFieldNames = expectedFieldNames.build(),
                    parameterValuesByPath = parameterValuesByPath.build(),
                    sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.build(),
                    queryComposerFunction = queryComposerFunction
                )
            }
        }

        internal class DefaultColumnarDocumentContext(
            override val expectedFieldNames: PersistentList<String> = persistentListOf(),
            override val parameterValuesByPath: PersistentMap<SchematicPath, JsonNode> =
                persistentMapOf(),
            override val sourceIndexPathsByFieldName: PersistentMap<String, SchematicPath> =
                persistentMapOf(),
            override val queryComposerFunction:
                Option<(ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition> =
                none()
        ) : ColumnarDocumentContext {

            override fun update(transformer: Builder.() -> Builder): ColumnarDocumentContext {
                return transformer(
                        DefaultColumnarDocumentContextBuilder(
                            expectedFieldNames = expectedFieldNames.builder(),
                            parameterValuesByPath = parameterValuesByPath.builder(),
                            sourceIndexPathsByFieldName = sourceIndexPathsByFieldName.builder(),
                            queryComposerFunction = queryComposerFunction
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
