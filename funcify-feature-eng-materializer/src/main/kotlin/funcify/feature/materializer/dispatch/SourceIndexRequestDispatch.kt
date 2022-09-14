package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.BackupExternalDataSourceCalculatedJsonValueRetriever
import funcify.feature.datasource.retrieval.ExternalDataSourceJsonValuesRetriever
import funcify.feature.datasource.retrieval.TrackableJsonValueRetriever
import funcify.feature.datasource.tracking.TrackableValue
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
            trackableJsonValueRetriever: TrackableJsonValueRetriever
        ): TrackableValueSourceIndexRetrievalSpec

        fun externalDataSourceJsonValuesRetriever(
            externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever
        ): MultipleSourceIndexRetrievalSpec
    }

    interface TrackableValueSourceIndexRetrievalSpec {

        fun dispatchedTrackableValueJsonRequest(
            dispatch: Mono<TrackableValue<JsonNode>>
        ): TrackableValueSourceIndexRetrievalSpec

        fun backupBaseExternalDataSourceJsonValuesRetriever(
            externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever
        ): TrackableValueSourceIndexRetrievalSpec

        fun backUpExternalDataSourceCalculatedJsonValueRetriever(
            backupExternalDataSourceCalculatedJsonValueRetriever:
                BackupExternalDataSourceCalculatedJsonValueRetriever
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

        val trackableJsonValueRetriever: TrackableJsonValueRetriever

        val dispatchedTrackableValueRequest: Mono<TrackableValue<JsonNode>>

        val backupBaseExternalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever

        val backupExternalDataSourceCalculatedJsonValueRetriever:
            BackupExternalDataSourceCalculatedJsonValueRetriever
    }

    interface ExternalDataSourceValuesDispatch : SourceIndexRequestDispatch {

        val externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever

        val dispatchedMultipleIndexRequest: Mono<ImmutableMap<SchematicPath, JsonNode>>
    }
}
