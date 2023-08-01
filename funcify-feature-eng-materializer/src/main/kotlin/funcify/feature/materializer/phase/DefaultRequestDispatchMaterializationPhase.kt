package funcify.feature.materializer.phase

import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch
import funcify.feature.schema.path.GQLOperationPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRequestDispatchMaterializationPhase(
    override val trackableSingleValueRequestDispatchesBySourceIndexPath:
        PersistentMap<GQLOperationPath, TrackableSingleJsonValueDispatch> = persistentMapOf(),
    override val externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath:
        PersistentMap<GQLOperationPath, ExternalDataSourceValuesDispatch> = persistentMapOf()
) : RequestDispatchMaterializationPhase {}
