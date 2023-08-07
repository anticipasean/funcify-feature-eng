package funcify.feature.materializer.schema.edge

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface RequestParameterEdge : SchematicEdge {

    interface MaterializedValueRequestParameterEdge : RequestParameterEdge {
        val materializedJsonValue: JsonNode
    }

    interface DependentValueRequestParameterEdge : RequestParameterEdge {
        val extractionFunction: (ImmutableMap<GQLOperationPath, JsonNode>) -> Option<JsonNode>
    }

    interface Builder {

        fun fromPathToPath(path1: GQLOperationPath, path2: GQLOperationPath): Builder

        fun fromPathToPath(edgeKey: Pair<GQLOperationPath, GQLOperationPath>): Builder

        fun materializedValue(materializedJsonNode: JsonNode): Builder

        fun dependentExtractionFunction(
            extractor: (ImmutableMap<GQLOperationPath, JsonNode>) -> Option<JsonNode>
        ): Builder

        fun build(): RequestParameterEdge
    }
}
