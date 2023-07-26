package funcify.feature.materializer.context.document

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import graphql.language.Document
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap

/**
 * @author smccarron
 * @created 2022-10-22
 */
interface ColumnarDocumentContext {

    companion object {

        val COLUMNAR_DOCUMENT_CONTEXT_KEY: String =
            ColumnarDocumentContext::class.qualifiedName + ".COLUMNAR_DOCUMENT_CONTEXT"
    }

    val expectedFieldNames: ImmutableList<String>

    val parameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>

    val sourceIndexPathsByFieldName: ImmutableMap<String, SchematicPath>

    val queryComposerFunction:
        Option<(ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition>

    val operationDefinition: Option<OperationDefinition>

    val document: Option<Document>

    fun update(transformer: Builder.() -> Builder): ColumnarDocumentContext

    interface Builder {

        fun expectedFieldNames(expectedFieldNames: List<String>): Builder

        fun parameterValuesByPath(
            parameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
        ): Builder

        fun addParameterValueForPath(path: SchematicPath, jsonValue: JsonNode): Builder

        fun removeParameterValueWithPath(path: SchematicPath): Builder

        fun sourceIndexPathsByFieldName(
            sourceIndexPathsByFieldName: PersistentMap<String, SchematicPath>
        ): Builder

        fun addSourceIndexPathForFieldName(fieldName: String, path: SchematicPath): Builder

        fun queryComposerFunction(
            function: (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition
        ): Builder

        fun operationDefinition(operationDefinition: OperationDefinition): Builder

        fun document(document: Document): Builder

        fun build(): ColumnarDocumentContext
    }
}
