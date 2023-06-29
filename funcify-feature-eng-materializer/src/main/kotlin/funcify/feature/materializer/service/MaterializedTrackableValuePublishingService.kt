package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.session.GraphQLSingleRequestSession

/**
 *
 * @author smccarron
 * @created 2022-09-13
 */
fun interface MaterializedTrackableValuePublishingService {

    fun publishMaterializedTrackableJsonValueIfApplicable(
        session: GraphQLSingleRequestSession,
        materializedTrackableJsonValue: TrackableValue<JsonNode>
    )
}
