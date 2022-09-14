package funcify.feature.materializer.phase

import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRequestDispatchMaterializationPhase(
    override val trackableSingleValueRequestDispatchesBySourceIndexPath:
        PersistentMap<SchematicPath, TrackableSingleJsonValueDispatch> = persistentMapOf(),
    override val externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath:
        PersistentMap<SchematicPath, ExternalDataSourceValuesDispatch> = persistentMapOf()
) : RequestDispatchMaterializationPhase {}
