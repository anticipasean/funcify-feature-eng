package funcify.feature.materializer.service

import funcify.feature.materializer.service.SourceIndexRequestDispatch.DispatchedCacheableSingleSourceIndexRetrieval
import funcify.feature.materializer.service.SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRequestDispatchMaterializationPhase(
    override val cacheableSingleSourceIndexRequestDispatchesBySourceIndexPath:
        PersistentMap<SchematicPath, DispatchedCacheableSingleSourceIndexRetrieval> = persistentMapOf(),
    override val multipleSourceIndexRequestDispatchesBySourceIndexPath:
        PersistentMap<SchematicPath, DispatchedMultiSourceIndexRetrieval> = persistentMapOf()
) : RequestDispatchMaterializationPhase {}
