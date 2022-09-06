package funcify.feature.materializer.service

import funcify.feature.materializer.service.SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval
import funcify.feature.materializer.service.SourceIndexRequestDispatch.DispatchedTrackableSingleSourceIndexRetrieval
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
