package funcify.feature.materializer.context.publishing

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableJsonValuePublisher
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.SchematicPath
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-11-14
 */
interface TrackableValuePublishingContext {

    val session: GraphQLSingleRequestSession

    val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch

    val publisher: TrackableJsonValuePublisher

    val calculatedValue: TrackableValue.CalculatedValue<JsonNode>

    val lastUpdatedInstantsByPath: ImmutableMap<SchematicPath, Instant>

    val entityIdentifierValuesByPath: ImmutableMap<SchematicPath, JsonNode>

    fun update(transformer: Builder.() -> Builder): TrackableValuePublishingContext

    interface Builder {

        fun graphQLSingleRequestSession(session: GraphQLSingleRequestSession): Builder

        fun dispatchedRequest(
            dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
        ): Builder

        fun trackableJsonValuePublisher(publisher: TrackableJsonValuePublisher): Builder

        fun calculatedValue(calculatedValue: TrackableValue.CalculatedValue<JsonNode>): Builder

        fun putLastUpdatedInstantForPath(path: SchematicPath, lastUpdatedInstant: Instant): Builder

        fun putAllLastUpdatedInstantsForPaths(
            lastUpdatedInstantsByPath: Map<SchematicPath, Instant>
        ): Builder

        fun removeLastUpdatedInstantForPath(path: SchematicPath): Builder

        fun clearLastUpdatedInstantsByPath(): Builder

        fun putEntityIdentifierValueForPath(
            path: SchematicPath,
            entityIdentifierValue: JsonNode
        ): Builder

        fun putAllEntityIdentifierValuesForPaths(
            entityIdentifiersByPath: Map<SchematicPath, JsonNode>
        ): Builder

        fun removeEntityIdentifierValueForPath(path: SchematicPath): Builder

        fun clearEntityIdentifierValuesForPaths(): Builder

        fun build(): TrackableValuePublishingContext
    }
}
