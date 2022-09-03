package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.BackupSingleSourceIndexJsonOptionRetrievalFunction
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

internal class DefaultSourceIndexRequestDispatchFactory : SourceIndexRequestDispatchFactory {

    companion object {

        internal class DefaultBuilder(
            private var sourceIndexPath: SchematicPath? = null,
            private var retrievalFunctionSpec: RetrievalFunctionSpec? = null
        ) : SourceIndexRequestDispatch.Builder {

            override fun sourceIndexPath(path: SchematicPath): SourceIndexRequestDispatch.Builder {
                this.sourceIndexPath = path
                return this
            }

            override fun retrievalFunctionSpec(
                retrievalFunctionSpec: RetrievalFunctionSpec
            ): SourceIndexRequestDispatch.Builder {
                this.retrievalFunctionSpec = retrievalFunctionSpec
                return this
            }

            override fun singleSourceIndexJsonOptionCacheRetrievalFunction(
                singleSourceIndexJsonOptionCacheRetrievalFunction:
                    SingleSourceIndexJsonOptionCacheRetrievalFunction
            ): SourceIndexRequestDispatch.CacheableSingleSourceIndexRetrievalSpec {
                return DefaultCacheableSingleSourceIndexRetrievalSpec(
                    sourceIndexPath,
                    retrievalFunctionSpec,
                    singleSourceIndexJsonOptionCacheRetrievalFunction
                )
            }

            override fun multipleSourceIndicesJsonRetrievalFunction(
                multipleSourceIndicesJsonRetrievalFunction:
                    MultipleSourceIndicesJsonRetrievalFunction
            ): SourceIndexRequestDispatch.MultipleSourceIndexRetrievalSpec {
                return DefaultMultipleSourceIndexRetrievalSpec(
                    sourceIndexPath,
                    retrievalFunctionSpec,
                    multipleSourceIndicesJsonRetrievalFunction
                )
            }
        }

        internal class DefaultCacheableSingleSourceIndexRetrievalSpec(
            private val sourceIndexPath: SchematicPath?,
            private val retrievalFunctionSpec: RetrievalFunctionSpec?,
            private val singleSourceIndexJsonOptionCacheRetrievalFunction:
                SingleSourceIndexJsonOptionCacheRetrievalFunction,
            private var dispatchedSingleIndexCacheRequest: Mono<JsonNode>? = null,
            private var backupBaseMultipleSourceIndicesJsonRetrievalFunction:
                MultipleSourceIndicesJsonRetrievalFunction? =
                null,
            private var backupFunction: BackupSingleSourceIndexJsonOptionRetrievalFunction? = null
        ) : SourceIndexRequestDispatch.CacheableSingleSourceIndexRetrievalSpec {

            override fun dispatchedSingleIndexCacheRequest(
                dispatch: Mono<JsonNode>
            ): SourceIndexRequestDispatch.CacheableSingleSourceIndexRetrievalSpec {
                this.dispatchedSingleIndexCacheRequest = dispatch
                return this
            }

            override fun backupBaseMultipleSourceIndicesJsonRetrievalFunction(
                multipleSourceIndicesJsonRetrievalFunction:
                    MultipleSourceIndicesJsonRetrievalFunction
            ): SourceIndexRequestDispatch.CacheableSingleSourceIndexRetrievalSpec {
                this.backupBaseMultipleSourceIndicesJsonRetrievalFunction =
                    multipleSourceIndicesJsonRetrievalFunction
                return this
            }

            override fun backupSingleSourceIndexJsonOptionRetrievalFunction(
                backupFunction: BackupSingleSourceIndexJsonOptionRetrievalFunction
            ): SourceIndexRequestDispatch.CacheableSingleSourceIndexRetrievalSpec {
                this.backupFunction = backupFunction
                return this
            }

            override fun build():
                SourceIndexRequestDispatch.DispatchedCacheableSingleSourceIndexRetrieval {
                return when {
                    sourceIndexPath == null ||
                        retrievalFunctionSpec == null ||
                        dispatchedSingleIndexCacheRequest == null ||
                        backupBaseMultipleSourceIndicesJsonRetrievalFunction == null ||
                        backupFunction == null -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "one or more required parameters is missing for dispatched_cacheable_single_source_index_retrieval instance creation"
                        )
                    }
                    else -> {
                        DefaultDispatchedCacheableSingleSourceIndexRetrieval(
                            sourceIndexPath,
                            retrievalFunctionSpec,
                            singleSourceIndexJsonOptionCacheRetrievalFunction,
                            dispatchedSingleIndexCacheRequest!!,
                            backupBaseMultipleSourceIndicesJsonRetrievalFunction!!,
                            backupFunction!!
                        )
                    }
                }
            }
        }

        internal class DefaultMultipleSourceIndexRetrievalSpec(
            private val sourceIndexPath: SchematicPath?,
            private val retrievalFunctionSpec: RetrievalFunctionSpec?,
            private val multipleSourceIndicesJsonRetrievalFunction:
                MultipleSourceIndicesJsonRetrievalFunction,
            private var dispatchedMultipleIndexRequest:
                Mono<ImmutableMap<SchematicPath, JsonNode>>? =
                null
        ) : SourceIndexRequestDispatch.MultipleSourceIndexRetrievalSpec {

            override fun dispatchedMultipleIndexRequest(
                dispatch: Mono<ImmutableMap<SchematicPath, JsonNode>>
            ): SourceIndexRequestDispatch.MultipleSourceIndexRetrievalSpec {
                this.dispatchedMultipleIndexRequest = dispatch
                return this
            }

            override fun build(): SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval {
                return when {
                    sourceIndexPath == null ||
                        retrievalFunctionSpec == null ||
                        dispatchedMultipleIndexRequest == null -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "one or more required parameters is missing for dispatched_multi_source_index_retrieval instance creation"
                        )
                    }
                    else -> {
                        DefaultDispatchedMultiSourceIndexRetrieval(
                            sourceIndexPath,
                            retrievalFunctionSpec,
                            multipleSourceIndicesJsonRetrievalFunction,
                            dispatchedMultipleIndexRequest!!
                        )
                    }
                }
            }
        }

        internal class DefaultDispatchedCacheableSingleSourceIndexRetrieval(
            override val sourceIndexPath: SchematicPath,
            override val retrievalFunctionSpec: RetrievalFunctionSpec,
            override val singleSourceIndexJsonOptionCacheRetrievalFunction:
                SingleSourceIndexJsonOptionCacheRetrievalFunction,
            override val dispatchedSingleIndexCacheRequest: Mono<JsonNode>,
            override val backupBaseMultipleSourceIndicesJsonRetrievalFunction:
                MultipleSourceIndicesJsonRetrievalFunction,
            override val backupSingleSourceIndexJsonOptionRetrievalFunction:
                BackupSingleSourceIndexJsonOptionRetrievalFunction
        ) : SourceIndexRequestDispatch.DispatchedCacheableSingleSourceIndexRetrieval {}

        internal class DefaultDispatchedMultiSourceIndexRetrieval(
            override val sourceIndexPath: SchematicPath,
            override val retrievalFunctionSpec: RetrievalFunctionSpec,
            override val multipleSourceIndicesJsonRetrievalFunction:
                MultipleSourceIndicesJsonRetrievalFunction,
            override val dispatchedMultipleIndexRequest: Mono<ImmutableMap<SchematicPath, JsonNode>>
        ) : SourceIndexRequestDispatch.DispatchedMultiSourceIndexRetrieval {}
    }

    override fun builder(): SourceIndexRequestDispatch.Builder {
        return DefaultBuilder()
    }
}
