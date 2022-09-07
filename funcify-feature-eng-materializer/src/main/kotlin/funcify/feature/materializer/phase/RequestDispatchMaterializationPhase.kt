package funcify.feature.materializer.phase

import funcify.feature.materializer.service.MaterializationPhase
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.DispatchedTrackableSingleSourceIndexRetrieval
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-26
 */
interface RequestDispatchMaterializationPhase : MaterializationPhase {

    val trackableSingleValueRequestDispatchesBySourceIndexPath:
        ImmutableMap<SchematicPath, DispatchedTrackableSingleSourceIndexRetrieval>

    val multipleSourceIndexRequestDispatchesBySourceIndexPath:
        ImmutableMap<SchematicPath, DispatchedMultiSourceIndexRetrieval>
}
