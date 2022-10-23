package funcify.feature.materializer.context.document

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap

/**
 *
 * @author smccarron
 * @created 2022-10-22
 */
interface ColumnarDocumentContext {

    val expectedFieldNames: ImmutableList<String>

    val parameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>

    val sourceIndexPathsByFieldName: ImmutableMap<String, SchematicPath>

    val queryComposerFunction:
        Option<(ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition>

    fun update(transformer: Builder.() -> Builder): ColumnarDocumentContext

    interface Builder {

        fun expectedFieldNames(expectedFieldNames: PersistentList<String>): Builder

        fun parameterValuesByPath(
            parameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
        ): Builder

        fun addParameterValueForPath(path: SchematicPath, jsonValue: JsonNode): Builder

        fun sourceIndexPathsByFieldName(
            sourceIndexPathsByFieldName: PersistentMap<String, SchematicPath>
        ): Builder

        fun addSourceIndexPathForFieldName(fieldName: String, path: SchematicPath): Builder

        fun queryComposerFunction(
            function: (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition
        ): Builder

        fun build(): ColumnarDocumentContext
    }
}
