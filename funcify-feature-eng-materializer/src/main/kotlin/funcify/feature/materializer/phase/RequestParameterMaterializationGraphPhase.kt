package funcify.feature.materializer.phase

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-25
 */
interface RequestParameterMaterializationGraphPhase {

    val requestGraph: PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
}
