package funcify.feature.materializer.phase

import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.DispatchedTrackableSingleSourceIndexRetrieval
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRequestDispatchMaterializationPhase(
    override val trackableSingleValueRequestDispatchesBySourceIndexPath:
        PersistentMap<SchematicPath, DispatchedTrackableSingleSourceIndexRetrieval> = persistentMapOf(),
    override val multipleSourceIndexRequestDispatchesBySourceIndexPath:
        PersistentMap<SchematicPath, DispatchedMultiSourceIndexRetrieval> = persistentMapOf()
) : RequestDispatchMaterializationPhase {}
