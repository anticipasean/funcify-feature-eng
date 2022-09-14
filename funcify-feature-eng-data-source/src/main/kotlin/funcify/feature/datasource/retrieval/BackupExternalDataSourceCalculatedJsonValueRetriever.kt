package funcify.feature.datasource.retrieval

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-28
 */
fun interface BackupExternalDataSourceCalculatedJsonValueRetriever :
    (TrackableValue<JsonNode>, ImmutableMap<SchematicPath, Mono<JsonNode>>) -> Mono<
            out TrackableValue<JsonNode>> {

    override fun invoke(
        trackableValue: TrackableValue<JsonNode>,
        parameterValuesByPath: ImmutableMap<SchematicPath, Mono<JsonNode>>
    ): Mono<out TrackableValue<JsonNode>>
}
