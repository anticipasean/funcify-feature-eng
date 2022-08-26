package funcify.feature.materializer.service

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
internal data class DefaultRequestParameterMaterializationGraphPhase(
    override val requestGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
    override val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>,
    override val parameterIndexPathsBySourceIndexPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>>,
    override val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>
) : RequestParameterMaterializationGraphPhase {}
