package funcify.feature.schema.dataelementsource.retrieval

import arrow.core.Either
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.right
import funcify.feature.schema.dataelementsource.retrieval.DataElementJsonValueSource.Builder
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultSchematicPathBasedJsonRetrievalFunctionFactory(
    private val dataSourceRepresentativeJsonRetrievalStrategyProviders:
        ImmutableSet<DataSourceRepresentativeJsonRetrievalStrategyProvider<*>> =
        persistentSetOf(),
    private val trackableValueJsonRetrievalStrategyProviders:
        ImmutableSet<TrackableValueJsonRetrievalStrategyProvider<*>> =
        persistentSetOf()
) : SchematicPathBasedJsonRetrievalFunctionFactory {

    companion object {

        internal class DefaultMultiSourceIndicesFunctionBuilder(
            private val dataSourceRepresentativeJsonRetrievalStrategyProviders:
                ImmutableSet<DataSourceRepresentativeJsonRetrievalStrategyProvider<*>> =
                persistentSetOf(),
            private var dataSource: DataElementSource<*>? = null,
            private var parameterVertices:
                PersistentSet.Builder<Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>().builder(),
            private var sourceVertices:
                PersistentSet.Builder<Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>().builder()
        ) : Builder {

            override fun dataSource(dataSource: DataElementSource<*>): Builder {
                this.dataSource = dataSource
                return this
            }

            override fun addRequestParameter(
                parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>
            ): Builder {
                this.parameterVertices.add(parameterJunctionOrLeafVertex)
                return this
            }

            override fun addRequestParameter(
                parameterJunctionVertex: ParameterJunctionVertex
            ): Builder {
                this.parameterVertices.add(parameterJunctionVertex.left())
                return this
            }

            override fun addRequestParameter(parameterLeafVertex: ParameterLeafVertex): Builder {
                this.parameterVertices.add(parameterLeafVertex.right())
                return this
            }

            override fun addSourceTarget(
                sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
            ): Builder {
                this.sourceVertices.add(sourceJunctionOrLeafVertex)
                return this
            }

            override fun addSourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder {
                this.sourceVertices.add(sourceJunctionVertex.left())
                return this
            }

            override fun addSourceTarget(sourceLeafVertex: SourceLeafVertex): Builder {
                this.sourceVertices.add(sourceLeafVertex.right())
                return this
            }

            override fun build(): Try<DataElementJsonValueSource> {
                return when {
                    dataSource == null -> {
                        ServiceError.of(
                                """data_source has not been provided 
                                |for ${DataElementJsonValueSource::class.qualifiedName} 
                                |creation""".flatten()
                            )
                            .failure()
                    }
                    sourceVertices.isEmpty() -> {
                        ServiceError.of(
                                """at least one source_vertex must be supplied 
                                |for the return value of this ${DataElementJsonValueSource::class.qualifiedName} 
                                |to have any mappings""".flatten()
                            )
                            .failure()
                    }
                    dataSourceRepresentativeJsonRetrievalStrategyProviders.none { strategyProvider
                        ->
                        strategyProvider.providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
                            dataSource!!.key
                        )
                    } -> {
                        ServiceError.of(
                                """no ${DataSourceRepresentativeJsonRetrievalStrategyProvider::class.qualifiedName} 
                                    |found that supports this type of data_source: 
                                    |[ actual: ${dataSource!!.key}  
                                    |]""".flatten()
                            )
                            .failure()
                    }
                    else -> {
                        Try.fromOption(
                                dataSourceRepresentativeJsonRetrievalStrategyProviders
                                    .firstOrNone { strategyProvider ->
                                        strategyProvider
                                            .providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
                                                dataSource!!.key
                                            )
                                    }
                            )
                            .flatMap { provider ->
                                createTypedDataSourceSpecificJsonRetrievalStrategyFor(
                                    provider,
                                    dataSource!!,
                                    sourceVertices.build(),
                                    parameterVertices.build()
                                )
                            }
                    }
                }
            }

            private fun <
                SI : SourceIndex<SI>> createTypedDataSourceSpecificJsonRetrievalStrategyFor(
                provider: DataSourceRepresentativeJsonRetrievalStrategyProvider<SI>,
                dataSource: DataElementSource<*>,
                sourceVertices: PersistentSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
                parameterVertices:
                    PersistentSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>
            ): Try<DataElementJsonValueSource> {
                // If already assessed as acceptable in earlier check, then this datasource must be
                // of this source_index type
                @Suppress("UNCHECKED_CAST")
                val typedDataSource: DataElementSource<SI> = dataSource as DataElementSource<SI>
                return provider.createExternalDataSourceJsonValuesRetrieverFor(
                    typedDataSource,
                    sourceVertices,
                    parameterVertices
                )
            }
        }

        internal class DefaultSingleSourceIndexCacheRetrievalFunctionBuilder(
            private val trackableValueJsonRetrievalStrategyProviders:
                ImmutableSet<TrackableValueJsonRetrievalStrategyProvider<*>> =
                persistentSetOf(),
            private var dataSource: DataElementSource<*>? = null
        ) : FeatureJsonValueStore.Builder {

            override fun cacheForDataSource(
                dataSource: DataElementSource<*>
            ): FeatureJsonValueStore.Builder {
                this.dataSource = dataSource
                return this
            }

            override fun build(): Try<FeatureJsonValueStore> {
                return when {
                    dataSource == null -> {
                        ServiceError.builder()
                            .message(
                                """data_source has not been provided 
                                |for ${FeatureJsonValueStore::class.qualifiedName} 
                                |creation""".flatten()
                            )
                            .build()
                            .failure()
                    }
                    trackableValueJsonRetrievalStrategyProviders.none { strategyProvider ->
                        strategyProvider
                            .providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
                                dataSource!!.key
                            )
                    } -> {
                        ServiceError.of(
                                """no ${TrackableValueJsonRetrievalStrategyProvider::class.qualifiedName} 
                                    |found that supports this type of data_source: 
                                    |[ actual: ${dataSource!!.key}  
                                    |]""".flatten()
                            )
                            .failure()
                    }
                    else -> {
                        Try.fromOption(
                                trackableValueJsonRetrievalStrategyProviders.firstOrNone {
                                    strategyProvider ->
                                    strategyProvider
                                        .providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
                                            dataSource!!.key
                                        )
                                }
                            )
                            .flatMap { provider ->
                                createTypedDataSourceCacheJsonRetrievalStrategyFor(
                                    provider,
                                    dataSource!!
                                )
                            }
                    }
                }
            }

            private fun <SI : SourceIndex<SI>> createTypedDataSourceCacheJsonRetrievalStrategyFor(
                provider: TrackableValueJsonRetrievalStrategyProvider<SI>,
                dataSource: DataElementSource<*>
            ): Try<FeatureJsonValueStore> {
                // If already assessed as acceptable in earlier check, then this datasource must be
                // of this source_index type
                @Suppress("UNCHECKED_CAST")
                val typedDataSource: DataElementSource<SI> = dataSource as DataElementSource<SI>
                return provider.createTrackableValueJsonRetrievalFunctionOnBehalfOf(typedDataSource)
            }
        }
    }

    override fun canBuildExternalDataSourceJsonValuesRetrieverForDataSource(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean {
        return dataSourceRepresentativeJsonRetrievalStrategyProviders.any { provider ->
            provider.providesJsonValueRetrieversForVerticesWithSourceIndicesIn(dataSourceKey)
        }
    }

    override fun canBuildTrackableJsonValueRetrieverOnBehalfOfDataSource(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean {
        return trackableValueJsonRetrievalStrategyProviders.any { provider ->
            provider.providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(dataSourceKey)
        }
    }

    override fun dataElementJsonValueStoreBuilder(): Builder {
        return DefaultMultiSourceIndicesFunctionBuilder(
            dataSourceRepresentativeJsonRetrievalStrategyProviders =
                dataSourceRepresentativeJsonRetrievalStrategyProviders
        )
    }

    override fun featureJsonValueStoreBuilder(): FeatureJsonValueStore.Builder {
        return DefaultSingleSourceIndexCacheRetrievalFunctionBuilder(
            trackableValueJsonRetrievalStrategyProviders =
                trackableValueJsonRetrievalStrategyProviders
        )
    }
}
