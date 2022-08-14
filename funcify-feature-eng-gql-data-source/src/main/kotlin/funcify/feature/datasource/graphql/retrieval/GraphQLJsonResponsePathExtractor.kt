package funcify.feature.datasource.graphql.retrieval

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

object GraphQLJsonResponsePathExtractor {

    fun extractPathToValueMappingsFromGraphQLJsonResponse(
        dataJsonObject: JsonNode
    ): ImmutableMap<SchematicPath, JsonNode> {
        return sequenceOf(GraphQLJsonNodePathTraversalContext(persistentListOf(), dataJsonObject))
            .recurse { ctx -> traverseGraphQLJsonDataResponse(ctx) }
            .reducePairsToPersistentMap()
    }

    private data class GraphQLJsonNodePathTraversalContext(
        val pathSegments: PersistentList<String>,
        val value: JsonNode
    )

    private fun traverseGraphQLJsonDataResponse(
        context: GraphQLJsonNodePathTraversalContext
    ): Sequence<Either<GraphQLJsonNodePathTraversalContext, Pair<SchematicPath, JsonNode>>> {
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
                        GraphQLJsonNodePathTraversalContext(
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
