package funcify.feature.materializer.schema

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.tools.SchematicPathBasedRetrievalFunction
import funcify.feature.schema.SchematicEdge

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface RequestParameterEdge : SchematicEdge {

    interface MaterializedValueRequestParameterEdge : RequestParameterEdge {
        val materializedJsonValue: JsonNode
    }

    interface PendingValueRequestParameterEdge : RequestParameterEdge {
        val retrievalFunction: SchematicPathBasedRetrievalFunction
    }
}
