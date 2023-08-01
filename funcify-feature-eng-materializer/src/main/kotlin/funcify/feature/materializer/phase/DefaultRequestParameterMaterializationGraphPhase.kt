package funcify.feature.materializer.phase

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-25
 */
internal data class DefaultRequestParameterMaterializationGraphPhase(
    override val requestGraph: PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>,
    override val materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>,
    override val parameterIndexPathsBySourceIndexPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
    override val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
) : RequestParameterMaterializationGraphPhase {}
