package funcify.feature.materializer.service

import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRequestDispatchMaterializationPhase(
    override val processedRetrievalFunctionSpecsBySourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec> =
        persistentMapOf(),
    override val remainingRetrievalFunctionSpecsBySourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec> =
        persistentMapOf(),
    override val multiSrcIndexFunctionBySourceIndexPath:
        PersistentMap<SchematicPath, MultipleSourceIndicesJsonRetrievalFunction> =
        persistentMapOf(),
    override val singleSrcIndexCacheFunctionBySourceIndexPath:
        PersistentMap<SchematicPath, SingleSourceIndexJsonOptionCacheRetrievalFunction> =
        persistentMapOf(),
) : RequestDispatchMaterializationPhase
