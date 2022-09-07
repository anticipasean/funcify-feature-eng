package funcify.feature.materializer.phase

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-25
 */
interface RequestParameterMaterializationGraphPhase {

    val requestGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>
}
