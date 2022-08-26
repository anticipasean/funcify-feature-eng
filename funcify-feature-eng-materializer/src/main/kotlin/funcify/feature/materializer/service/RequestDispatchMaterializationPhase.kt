package funcify.feature.materializer.service

import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap

/**
 *
 * @author smccarron
 * @created 2022-08-26
 */
interface RequestDispatchMaterializationPhase : MaterializationPhase {

    val processedRetrievalFunctionSpecsBySourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>

    val remainingRetrievalFunctionSpecsBySourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>

    val multiSrcIndexFunctionBySourceIndexPath:
        PersistentMap<SchematicPath, MultipleSourceIndicesJsonRetrievalFunction>

    val singleSrcIndexCacheFunctionBySourceIndexPath:
        PersistentMap<SchematicPath, SingleSourceIndexJsonOptionCacheRetrievalFunction>
}
