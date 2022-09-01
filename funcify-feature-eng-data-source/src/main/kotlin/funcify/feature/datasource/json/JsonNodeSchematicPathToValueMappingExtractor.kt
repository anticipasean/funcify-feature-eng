package funcify.feature.datasource.json

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
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
            JsonNodeType.STRING -> {
                sequenceOf(
                    (SchematicPath.of { pathSegments(context.pathSegments) } to context.value)
                        .right()
                )
            }
            JsonNodeType.ARRAY -> {
                // TODO: Revisit whether array nodes should be traversed if on root
                context.pathSegments
                    .toOption()
                    .filter { ps -> ps.isNotEmpty() }
                    .map { ps -> (SchematicPath.of { pathSegments(ps) } to context.value).right() }
                    .fold(::emptySequence, ::sequenceOf)
                    .plus(
                        context.pathSegments
                            .toOption()
                            .filter { ps -> ps.isNotEmpty() }
                            .and(context.value.toOption())
                            .filterIsInstance<ArrayNode>()
                            .map { an -> an.asSequence().withIndex() }
                            .fold(::emptySequence, ::identity)
                            .map { indexedValue: IndexedValue<JsonNode> ->
                                JsonNodePathTraversalContext(
                                        pathSegments =
                                            context.pathSegments
                                                .lastOrNone()
                                                .map { s ->
                                                    StringBuilder(s)
                                                        .append('[')
                                                        .append(indexedValue.index)
                                                        .append(']')
                                                        .toString()
                                                }
                                                .fold(::persistentListOf) { lastSegment ->
                                                    context.pathSegments
                                                        .removeAt(context.pathSegments.size - 1)
                                                        .add(lastSegment)
                                                },
                                        value = indexedValue.value
                                    )
                                    .left()
                            }
                    )
            }
            JsonNodeType.OBJECT -> {
                context.pathSegments
                    .toOption()
                    .map { ps -> (SchematicPath.of { pathSegments(ps) } to context.value).right() }
                    .fold(::emptySequence, ::sequenceOf)
                    .plus(
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
                    )
            }
            else -> emptySequence()
        }
    }
}
