package funcify.feature.datasource.retrieval

import arrow.core.Either
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.right
import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction.Builder
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
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
    private val dataSourceCacheJsonRetrievalStrategyProviders:
        ImmutableSet<DataSourceCacheJsonRetrievalStrategyProvider<*>> =
        persistentSetOf()
) : SchematicPathBasedJsonRetrievalFunctionFactory {

    companion object {

        internal class DefaultMultiSourceIndicesFunctionBuilder(
            private val dataSourceRepresentativeJsonRetrievalStrategyProviders:
                ImmutableSet<DataSourceRepresentativeJsonRetrievalStrategyProvider<*>> =
                persistentSetOf(),
            private var dataSource: DataSource<*>? = null,
            private var parameterVertices:
                PersistentSet.Builder<Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>().builder(),
            private var sourceVertices:
                PersistentSet.Builder<Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>().builder()
        ) : Builder {

            override fun dataSource(dataSource: DataSource<*>): Builder {
                this.dataSource = dataSource
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

            override fun addSourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder {
                this.sourceVertices.add(sourceJunctionVertex.left())
                return this
            }

            override fun addSourceTarget(sourceLeafVertex: SourceLeafVertex): Builder {
                this.sourceVertices.add(sourceLeafVertex.right())
                return this
            }

            override fun build(): Try<MultipleSourceIndicesJsonRetrievalFunction> {
                return when {
                    dataSource == null -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """data_source has not been provided 
                                |for ${MultipleSourceIndicesJsonRetrievalFunction::class.qualifiedName} 
                                |creation""".flatten()
                            )
                            .failure()
                    }
                    sourceVertices.isEmpty() -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one source_vertex must be supplied 
                                |for the return value of this ${MultipleSourceIndicesJsonRetrievalFunction::class.qualifiedName} 
                                |to have any mappings""".flatten()
                            )
                            .failure()
                    }
                    dataSourceRepresentativeJsonRetrievalStrategyProviders.none { strategyProvider
                        ->
                        strategyProvider
                            .providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
                                dataSource!!.key
                            )
                    } -> {
                        DataSourceException(
                                DataSourceErrorResponse.STRATEGY_MISSING,
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
                                            .providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
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
                dataSource: DataSource<*>,
                sourceVertices: PersistentSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
                parameterVertices:
                    PersistentSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>
            ): Try<MultipleSourceIndicesJsonRetrievalFunction> {
                // If already assessed as acceptable in earlier check, then this datasource must be
                // of this source_index type
                @Suppress("UNCHECKED_CAST")
                val typedDataSource: DataSource<SI> = dataSource as DataSource<SI>
                return provider.createMultipleSourceIndicesJsonRetrievalFunctionFor(
                    typedDataSource,
                    sourceVertices,
                    parameterVertices
                )
            }
        }

        internal class DefaultSingleSourceIndexCacheRetrievalFunctionBuilder(
            private val dataSourceCacheJsonRetrievalStrategyProviders:
                ImmutableSet<DataSourceCacheJsonRetrievalStrategyProvider<*>> =
                persistentSetOf(),
            private var dataSource: DataSource<*>? = null,
            private var sourceJunctionVertex: SourceJunctionVertex? = null,
            private var sourceLeafVertex: SourceLeafVertex? = null
        ) : SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder {

            override fun cacheForDataSource(
                dataSource: DataSource<*>
            ): SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder {
                this.dataSource = dataSource
                return this
            }

            override fun sourceTarget(
                sourceJunctionVertex: SourceJunctionVertex
            ): SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder {
                this.sourceJunctionVertex = sourceJunctionVertex
                return this
            }

            override fun sourceTarget(
                sourceLeafVertex: SourceLeafVertex
            ): SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder {
                this.sourceLeafVertex = sourceLeafVertex
                return this
            }

            override fun build(): Try<SingleSourceIndexJsonOptionCacheRetrievalFunction> {
                return when {
                    dataSource == null -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """data_source has not been provided 
                                |for ${SingleSourceIndexJsonOptionCacheRetrievalFunction::class.qualifiedName} 
                                |creation""".flatten()
                            )
                            .failure()
                    }
                    sourceJunctionVertex == null && sourceLeafVertex == null -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one source_vertex must be supplied 
                                |for the return value of this 
                                |${SingleSourceIndexJsonOptionCacheRetrievalFunction::class.qualifiedName} 
                                |to have any mappings""".flatten()
                            )
                            .failure()
                    }
                    dataSourceCacheJsonRetrievalStrategyProviders.none { strategyProvider ->
                        strategyProvider
                            .providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
                                dataSource!!.key
                            )
                    } -> {
                        DataSourceException(
                                DataSourceErrorResponse.STRATEGY_MISSING,
                                """no ${DataSourceCacheJsonRetrievalStrategyProvider::class.qualifiedName} 
                                    |found that supports this type of data_source: 
                                    |[ actual: ${dataSource!!.key}  
                                    |]""".flatten()
                            )
                            .failure()
                    }
                    else -> {
                        Try.fromOption(
                                dataSourceCacheJsonRetrievalStrategyProviders.firstOrNone {
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
                                    dataSource!!,
                                    (when {
                                        sourceJunctionVertex != null -> {
                                            sourceJunctionVertex!!.left()
                                        }
                                        sourceLeafVertex != null -> {
                                            sourceLeafVertex!!.right()
                                        }
                                        else -> {
                                            null
                                        }
                                    })!!
                                )
                            }
                    }
                }
            }

            private fun <SI : SourceIndex<SI>> createTypedDataSourceCacheJsonRetrievalStrategyFor(
                provider: DataSourceCacheJsonRetrievalStrategyProvider<SI>,
                dataSource: DataSource<*>,
                sourceVertex: Either<SourceJunctionVertex, SourceLeafVertex>
            ): Try<SingleSourceIndexJsonOptionCacheRetrievalFunction> {
                // If already assessed as acceptable in earlier check, then this datasource must be
                // of this source_index type
                @Suppress("UNCHECKED_CAST")
                val typedDataSource: DataSource<SI> = dataSource as DataSource<SI>
                return provider.createSingleSourceIndexJsonOptionRetrievalFunctionForCacheFor(
                    typedDataSource,
                    sourceVertex
                )
            }
        }
    }

    override fun multipleSourceIndicesJsonRetrievalFunctionBuilder(): Builder {
        return DefaultMultiSourceIndicesFunctionBuilder(
            dataSourceRepresentativeJsonRetrievalStrategyProviders =
                dataSourceRepresentativeJsonRetrievalStrategyProviders
        )
    }

    override fun singleSourceIndexCacheRetrievalFunctionBuilder():
        SingleSourceIndexJsonOptionCacheRetrievalFunction.Builder {
        return DefaultSingleSourceIndexCacheRetrievalFunctionBuilder(
            dataSourceCacheJsonRetrievalStrategyProviders =
                dataSourceCacheJsonRetrievalStrategyProviders
        )
    }
}
