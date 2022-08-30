package funcify.feature.materializer.service

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.BackupSingleSourceIndexJsonOptionRetrievalFunction
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap

interface SourceIndexRequestDispatch {

    val sourceIndexPath: SchematicPath

    val retrievalFunctionSpec: RetrievalFunctionSpec

    interface Builder {

        fun sourceIndexPath(path: SchematicPath): Builder

        fun retrievalFunctionSpec(retrievalFunctionSpec: RetrievalFunctionSpec): Builder

        fun singleSourceIndexJsonOptionCacheRetrievalFunction(
            singleSourceIndexJsonOptionCacheRetrievalFunction:
                SingleSourceIndexJsonOptionCacheRetrievalFunction
        ): CacheableSingleSourceIndexRetrievalSpec

        fun multipleSourceIndicesJsonRetrievalFunction(
            multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction
        ): MultipleSourceIndexRetrievalSpec
    }

    interface CacheableSingleSourceIndexRetrievalSpec {

        fun dispatchedSingleIndexCacheRequest(
            dispatch: Deferred<Option<JsonNode>>
        ): CacheableSingleSourceIndexRetrievalSpec

        fun backupBaseMultipleSourceIndicesJsonRetrievalFunction(
            multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction
        ): CacheableSingleSourceIndexRetrievalSpec

        fun backupSingleSourceIndexJsonOptionRetrievalFunction(
            backupFunction: BackupSingleSourceIndexJsonOptionRetrievalFunction
        ): CacheableSingleSourceIndexRetrievalSpec

        fun build(): DispatchedCacheableSingleSourceIndexRetrieval
    }

    interface MultipleSourceIndexRetrievalSpec {

        fun dispatchedMultipleIndexRequest(
            dispatch: Deferred<ImmutableMap<SchematicPath, JsonNode>>
        ): MultipleSourceIndexRetrievalSpec

        fun build(): DispatchedMultiSourceIndexRetrieval
    }

    interface DispatchedCacheableSingleSourceIndexRetrieval : SourceIndexRequestDispatch {

        val singleSourceIndexJsonOptionCacheRetrievalFunction:
            SingleSourceIndexJsonOptionCacheRetrievalFunction

        val dispatchedSingleIndexCacheRequest: Deferred<Option<JsonNode>>

        val backupBaseMultipleSourceIndicesJsonRetrievalFunction:
            MultipleSourceIndicesJsonRetrievalFunction

        val backupSingleSourceIndexJsonOptionRetrievalFunction:
            BackupSingleSourceIndexJsonOptionRetrievalFunction
    }

    interface DispatchedMultiSourceIndexRetrieval : SourceIndexRequestDispatch {

        val multipleSourceIndicesJsonRetrievalFunction: MultipleSourceIndicesJsonRetrievalFunction

        val dispatchedMultipleIndexRequest: Deferred<ImmutableMap<SchematicPath, JsonNode>>
    }
}
