package funcify.feature.materializer.context.publishing

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.GQLOperationPath
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

    val publisher: FeatureJsonValuePublisher

    val calculatedValue: TrackableValue.CalculatedValue<JsonNode>

    val lastUpdatedInstantsByPath: ImmutableMap<GQLOperationPath, Instant>

    val entityIdentifierValuesByPath: ImmutableMap<GQLOperationPath, JsonNode>

    fun update(transformer: Builder.() -> Builder): TrackableValuePublishingContext

    interface Builder {

        fun graphQLSingleRequestSession(session: GraphQLSingleRequestSession): Builder

        fun dispatchedRequest(
            dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch
        ): Builder

        fun trackableJsonValuePublisher(publisher: FeatureJsonValuePublisher): Builder

        fun calculatedValue(calculatedValue: TrackableValue.CalculatedValue<JsonNode>): Builder

        fun putLastUpdatedInstantForPath(path: GQLOperationPath, lastUpdatedInstant: Instant): Builder

        fun putAllLastUpdatedInstantsForPaths(
            lastUpdatedInstantsByPath: Map<GQLOperationPath, Instant>
        ): Builder

        fun removeLastUpdatedInstantForPath(path: GQLOperationPath): Builder

        fun clearLastUpdatedInstantsByPath(): Builder

        fun putEntityIdentifierValueForPath(
            path: GQLOperationPath,
            entityIdentifierValue: JsonNode
        ): Builder

        fun putAllEntityIdentifierValuesForPaths(
            entityIdentifiersByPath: Map<GQLOperationPath, JsonNode>
        ): Builder

        fun removeEntityIdentifierValueForPath(path: GQLOperationPath): Builder

        fun clearEntityIdentifierValuesForPaths(): Builder

        fun build(): TrackableValuePublishingContext
    }
}
