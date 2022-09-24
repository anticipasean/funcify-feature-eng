package funcify.feature.materializer.phase

import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-26
 */
interface RequestDispatchMaterializationPhase : MaterializationPhase {

    val trackableSingleValueRequestDispatchesBySourceIndexPath:
        ImmutableMap<SchematicPath, TrackableSingleJsonValueDispatch>

    val externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath:
        ImmutableMap<SchematicPath, ExternalDataSourceValuesDispatch>
}
