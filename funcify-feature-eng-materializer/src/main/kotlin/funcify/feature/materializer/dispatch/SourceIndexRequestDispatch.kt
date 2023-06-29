package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.dataelementsource.retrieval.BackupExternalDataSourceCalculatedJsonValueRetriever
import funcify.feature.schema.dataelementsource.retrieval.DataElementJsonValueSource
import funcify.feature.schema.dataelementsource.retrieval.FeatureJsonValueStore
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

interface SourceIndexRequestDispatch {

    val sourceIndexPath: SchematicPath

    val retrievalFunctionSpec: RetrievalFunctionSpec

    interface Builder {

        fun sourceIndexPath(path: SchematicPath): Builder

        fun retrievalFunctionSpec(retrievalFunctionSpec: RetrievalFunctionSpec): Builder

        fun trackableJsonValueRetriever(
            featureJsonValueStore: FeatureJsonValueStore
        ): TrackableValueSourceIndexRetrievalSpec

        fun externalDataSourceJsonValuesRetriever(
            dataElementJsonValueSource: DataElementJsonValueSource
        ): MultipleSourceIndexRetrievalSpec
    }

    interface TrackableValueSourceIndexRetrievalSpec {

        fun dispatchedTrackableValueJsonRequest(
            dispatch: Mono<TrackableValue<JsonNode>>
        ): TrackableValueSourceIndexRetrievalSpec

        fun backupBaseExternalDataSourceJsonValuesRetriever(
            dataElementJsonValueSource: DataElementJsonValueSource
        ): TrackableValueSourceIndexRetrievalSpec

        fun backUpExternalDataSourceCalculatedJsonValueRetriever(
            backupExternalDataSourceCalculatedJsonValueRetriever: BackupExternalDataSourceCalculatedJsonValueRetriever
        ): TrackableValueSourceIndexRetrievalSpec

        fun build(): TrackableSingleJsonValueDispatch
    }

    interface MultipleSourceIndexRetrievalSpec {

        fun dispatchedMultipleIndexRequest(
            dispatch: Mono<ImmutableMap<SchematicPath, JsonNode>>
        ): MultipleSourceIndexRetrievalSpec

        fun build(): ExternalDataSourceValuesDispatch
    }

    interface TrackableSingleJsonValueDispatch : SourceIndexRequestDispatch {

        val featureJsonValueStore: FeatureJsonValueStore

        val dispatchedTrackableValueRequest: Mono<TrackableValue<JsonNode>>

        val backupBaseDataElementJsonValueSource: DataElementJsonValueSource

        val backupExternalDataSourceCalculatedJsonValueRetriever: BackupExternalDataSourceCalculatedJsonValueRetriever
    }

    interface ExternalDataSourceValuesDispatch : SourceIndexRequestDispatch {

        val dataElementJsonValueSource: DataElementJsonValueSource

        val dispatchedMultipleIndexRequest: Mono<ImmutableMap<SchematicPath, JsonNode>>
    }
}
