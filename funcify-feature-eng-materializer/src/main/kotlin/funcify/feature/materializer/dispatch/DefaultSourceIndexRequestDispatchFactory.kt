package funcify.feature.materializer.dispatch

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.BackupExternalDataSourceCalculatedJsonValueRetriever
import funcify.feature.datasource.retrieval.ExternalDataSourceJsonValuesRetriever
import funcify.feature.datasource.retrieval.TrackableJsonValueRetriever
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.naming.StandardNamingConventions
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

            override fun trackableJsonValueRetriever(
                trackableJsonValueRetriever: TrackableJsonValueRetriever
            ): SourceIndexRequestDispatch.TrackableValueSourceIndexRetrievalSpec {
                return DefaultTrackableValueSourceIndexRetrievalSpec(
                    sourceIndexPath,
                    retrievalFunctionSpec,
                    trackableJsonValueRetriever
                )
            }

            override fun externalDataSourceJsonValuesRetriever(
                externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever
            ): SourceIndexRequestDispatch.MultipleSourceIndexRetrievalSpec {
                return DefaultMultipleSourceIndexRetrievalSpec(
                    sourceIndexPath,
                    retrievalFunctionSpec,
                    externalDataSourceJsonValuesRetriever
                )
            }
        }

        internal class DefaultTrackableValueSourceIndexRetrievalSpec(
            private val sourceIndexPath: SchematicPath?,
            private val retrievalFunctionSpec: RetrievalFunctionSpec?,
            private val trackableJsonValueRetriever: TrackableJsonValueRetriever,
            private var dispatchedSingleIndexCacheRequest: Mono<TrackableValue<JsonNode>>? = null,
            private var backupBaseExternalDataSourceJsonValuesRetriever:
                ExternalDataSourceJsonValuesRetriever? =
                null,
            private var backupFunction: BackupExternalDataSourceCalculatedJsonValueRetriever? = null
        ) : SourceIndexRequestDispatch.TrackableValueSourceIndexRetrievalSpec {

            override fun dispatchedTrackableValueJsonRequest(
                dispatch: Mono<TrackableValue<JsonNode>>
            ): SourceIndexRequestDispatch.TrackableValueSourceIndexRetrievalSpec {
                this.dispatchedSingleIndexCacheRequest = dispatch
                return this
            }

            override fun backupBaseExternalDataSourceJsonValuesRetriever(
                externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever
            ): SourceIndexRequestDispatch.TrackableValueSourceIndexRetrievalSpec {
                this.backupBaseExternalDataSourceJsonValuesRetriever =
                    externalDataSourceJsonValuesRetriever
                return this
            }

            override fun backUpExternalDataSourceCalculatedJsonValueRetriever(
                backupExternalDataSourceCalculatedJsonValueRetriever:
                    BackupExternalDataSourceCalculatedJsonValueRetriever
            ): SourceIndexRequestDispatch.TrackableValueSourceIndexRetrievalSpec {
                this.backupFunction = backupExternalDataSourceCalculatedJsonValueRetriever
                return this
            }

            override fun build(): SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch {
                return when {
                    sourceIndexPath == null ||
                        retrievalFunctionSpec == null ||
                        dispatchedSingleIndexCacheRequest == null ||
                        backupBaseExternalDataSourceJsonValuesRetriever == null ||
                        backupFunction == null -> {
                        val conventionalName =
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch::class
                                    .simpleName
                                    ?: "<NA>"
                            )
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "one or more required parameters is missing for %s instance creation".format(
                                conventionalName
                            )
                        )
                    }
                    else -> {
                        DefaultTrackableSingleJsonValueDispatch(
                            sourceIndexPath,
                            retrievalFunctionSpec,
                            trackableJsonValueRetriever,
                            dispatchedSingleIndexCacheRequest!!,
                            backupBaseExternalDataSourceJsonValuesRetriever!!,
                            backupFunction!!
                        )
                    }
                }
            }
        }

        internal class DefaultMultipleSourceIndexRetrievalSpec(
            private val sourceIndexPath: SchematicPath?,
            private val retrievalFunctionSpec: RetrievalFunctionSpec?,
            private val externalDataSourceJsonValuesRetriever:
                ExternalDataSourceJsonValuesRetriever,
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

            override fun build(): SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch {
                return when {
                    sourceIndexPath == null ||
                        retrievalFunctionSpec == null ||
                        dispatchedMultipleIndexRequest == null -> {
                        val conventionalName =
                            StandardNamingConventions.SNAKE_CASE.deriveName(
                                SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch::class
                                    .simpleName
                                    ?: "<NA>"
                            )
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "one or more required parameters is missing for %s instance creation".format(
                                conventionalName
                            )
                        )
                    }
                    else -> {
                        DefaultExternalDataSourceValuesDispatch(
                            sourceIndexPath,
                            retrievalFunctionSpec,
                            externalDataSourceJsonValuesRetriever,
                            dispatchedMultipleIndexRequest!!
                        )
                    }
                }
            }
        }

        internal data class DefaultTrackableSingleJsonValueDispatch(
            override val sourceIndexPath: SchematicPath,
            override val retrievalFunctionSpec: RetrievalFunctionSpec,
            override val trackableJsonValueRetriever: TrackableJsonValueRetriever,
            override val dispatchedTrackableValueRequest: Mono<TrackableValue<JsonNode>>,
            override val backupBaseExternalDataSourceJsonValuesRetriever:
                ExternalDataSourceJsonValuesRetriever,
            override val backupExternalDataSourceCalculatedJsonValueRetriever:
                BackupExternalDataSourceCalculatedJsonValueRetriever
        ) : SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch {}

        internal data class DefaultExternalDataSourceValuesDispatch(
            override val sourceIndexPath: SchematicPath,
            override val retrievalFunctionSpec: RetrievalFunctionSpec,
            override val externalDataSourceJsonValuesRetriever:
                ExternalDataSourceJsonValuesRetriever,
            override val dispatchedMultipleIndexRequest: Mono<ImmutableMap<SchematicPath, JsonNode>>
        ) : SourceIndexRequestDispatch.ExternalDataSourceValuesDispatch {}
    }

    override fun builder(): SourceIndexRequestDispatch.Builder {
        return DefaultBuilder()
    }
}
