package funcify.feature.materializer.schema

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.tools.SchematicPathBasedJsonRetrievalFunction
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface RequestParameterEdge : SchematicEdge {

    interface MaterializedValueRequestParameterEdge : RequestParameterEdge {
        val materializedJsonValue: JsonNode
    }

    interface RetrievalFunctionRequestParameterEdge : RequestParameterEdge {
        val retrievalFunction: SchematicPathBasedJsonRetrievalFunction
    }

    interface Builder {

        fun fromPathToPath(path1: SchematicPath, path2: SchematicPath): Builder

        fun materializedValue(materializedJsonNode: JsonNode): Builder
    }
}
