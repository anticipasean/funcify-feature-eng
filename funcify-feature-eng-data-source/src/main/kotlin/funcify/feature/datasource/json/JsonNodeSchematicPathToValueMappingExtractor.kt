package funcify.feature.datasource.json

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

object JsonNodeSchematicPathToValueMappingExtractor :
    (JsonNode) -> ImmutableMap<SchematicPath, JsonNode> {

    override fun invoke(dataJsonObject: JsonNode): ImmutableMap<SchematicPath, JsonNode> {
        return sequenceOf(JsonNodePathTraversalContext(persistentListOf(), dataJsonObject))
            .recurse { ctx -> traverseJsonNodeContextCreatingPathsForEachNonObjectValue(ctx) }
            .sortedBy { (sp, _) -> sp }
            .reducePairsToPersistentMap()
    }

    private data class JsonNodePathTraversalContext(
        val pathSegments: PersistentList<String>,
        val value: JsonNode
    )

    private fun traverseJsonNodeContextCreatingPathsForEachNonObjectValue(
        context: JsonNodePathTraversalContext
    ): Sequence<Either<JsonNodePathTraversalContext, Pair<SchematicPath, JsonNode>>> {
        return when (context.value.nodeType) {
            JsonNodeType.NULL,
            JsonNodeType.MISSING,
            JsonNodeType.NUMBER,
            JsonNodeType.BINARY,
            JsonNodeType.STRING,
            JsonNodeType.ARRAY -> {
                sequenceOf(
                    (SchematicPath.of { pathSegments(context.pathSegments) } to context.value)
                        .right()
                )
            }
            JsonNodeType.OBJECT -> {
                context.value
                    .toOption()
                    .filterIsInstance<ObjectNode>()
                    .map { on -> on.fields().asSequence() }
                    .fold(::emptySequence, ::identity)
                    .map { (key, jsonValue) ->
                        JsonNodePathTraversalContext(
                                pathSegments = context.pathSegments.add(key),
                                value = jsonValue
                            )
                            .left()
                    }
            }
            else -> emptySequence()
        }
    }
}
