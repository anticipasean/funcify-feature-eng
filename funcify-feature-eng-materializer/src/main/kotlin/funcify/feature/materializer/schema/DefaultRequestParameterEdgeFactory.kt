package funcify.feature.materializer.schema

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.RequestParameterEdge.MaterializedValueRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionRequestParameterEdge
import funcify.feature.materializer.tools.SchematicPathBasedJsonRetrievalFunction
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultRequestParameterEdgeFactory : RequestParameterEdgeFactory {

    companion object {

        internal class DefaultMaterializedValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val materializedJsonValue: JsonNode
        ) : MaterializedValueRequestParameterEdge {}

        internal class DefaultRetrievalFunctionRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val retrievalFunction: SchematicPathBasedJsonRetrievalFunction,
        ) : RetrievalFunctionRequestParameterEdge {}

        internal class DefaultBuilder(var pathPair: Pair<SchematicPath, SchematicPath>? = null) :
            RequestParameterEdge.Builder {

            override fun fromPathToPath(
                path1: SchematicPath,
                path2: SchematicPath
            ): RequestParameterEdge.Builder {
                this.pathPair = path1 to path2
                return this
            }

            override fun materializedValue(
                materializedJsonNode: JsonNode
            ): RequestParameterEdge.Builder {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): RequestParameterEdge.Builder {
        TODO("Not yet implemented")
    }
}
