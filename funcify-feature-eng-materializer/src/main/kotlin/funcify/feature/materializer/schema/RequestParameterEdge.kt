package funcify.feature.materializer.schema

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.path.SchematicPath
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
        val extractionFunction: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
    }

    interface Builder {

        fun fromPathToPath(path1: SchematicPath, path2: SchematicPath): Builder

        fun fromPathToPath(edgeKey: Pair<SchematicPath, SchematicPath>): Builder

        fun materializedValue(materializedJsonNode: JsonNode): Builder

        fun dependentExtractionFunction(
            extractor: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
        ): Builder

        fun build(): RequestParameterEdge
    }
}
