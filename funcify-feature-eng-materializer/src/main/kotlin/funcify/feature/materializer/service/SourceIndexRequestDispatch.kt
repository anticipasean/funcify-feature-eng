package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.BackupTrackableValueRetrievalFunction
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.datasource.retrieval.TrackableValueJsonRetrievalFunction
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

        fun trackableValueJsonRetrievalFunction(
            trackableValueJsonRetrievalFunction: TrackableValueJsonRetrievalFunction
        ): TrackableValueSourceIndexRetrievalSpec

        fun multipleSourceIndicesJsonRetrievalFunction(
            multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction
        ): MultipleSourceIndexRetrievalSpec
    }

    interface TrackableValueSourceIndexRetrievalSpec {

        fun dispatchedTrackableValueJsonRequest(
            dispatch: Mono<TrackableValue<JsonNode>>
                                               ): TrackableValueSourceIndexRetrievalSpec

        fun backupBaseMultipleSourceIndicesJsonRetrievalFunction(
            multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction
        ): TrackableValueSourceIndexRetrievalSpec

        fun backupSingleSourceIndexJsonOptionRetrievalFunction(
            backupFunction: BackupTrackableValueRetrievalFunction
        ): TrackableValueSourceIndexRetrievalSpec

        fun build(): DispatchedCacheableSingleSourceIndexRetrieval
    }

    interface MultipleSourceIndexRetrievalSpec {

        fun dispatchedMultipleIndexRequest(
            dispatch: Mono<ImmutableMap<SchematicPath, JsonNode>>
        ): MultipleSourceIndexRetrievalSpec

        fun build(): DispatchedMultiSourceIndexRetrieval
    }

    interface DispatchedCacheableSingleSourceIndexRetrieval : SourceIndexRequestDispatch {

        val trackableValueJsonRetrievalFunction: TrackableValueJsonRetrievalFunction

        val dispatchedTrackableValueRequest: Mono<TrackableValue<JsonNode>>

        val backupBaseMultipleSourceIndicesJsonRetrievalFunction:
            MultipleSourceIndicesJsonRetrievalFunction

        val backupTrackableValueRetrievalFunction:
            BackupTrackableValueRetrievalFunction
    }

    interface DispatchedMultiSourceIndexRetrieval : SourceIndexRequestDispatch {

        val multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction

        val dispatchedMultipleIndexRequest: Mono<ImmutableMap<SchematicPath, JsonNode>>
    }
}
