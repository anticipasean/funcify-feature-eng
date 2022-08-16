package funcify.feature.datasource.retrieval

import arrow.core.Either
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.right
import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunction.Builder
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
class DefaultSchematicPathBasedJsonRetrievalFunctionFactory(
    val dataSourceSpecificJsonRetrievalStrategyProviders:
        ImmutableSet<DataSourceSpecificJsonRetrievalStrategyProvider<*>> =
        persistentSetOf()
) : SchematicPathBasedJsonRetrievalFunctionFactory {

    companion object {

        internal class DefaultBuilder(
            private val dataSourceSpecificJsonRetrievalStrategyProviders:
                ImmutableSet<DataSourceSpecificJsonRetrievalStrategyProvider<*>> =
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

            override fun build(): Try<SchematicPathBasedJsonRetrievalFunction> {
                return when {
                    dataSource == null -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """data_source has not been provided 
                                |for ${SchematicPathBasedJsonRetrievalFunction::class.qualifiedName} 
                                |creation""".flatten()
                            )
                            .failure()
                    }
                    sourceVertices.isEmpty() -> {
                        DataSourceException(
                                DataSourceErrorResponse.MISSING_PARAMETER,
                                """at least one source_vertex must supplied 
                                |for the return value of this ${SchematicPathBasedJsonRetrievalFunction::class.qualifiedName} 
                                |to have any mappings""".flatten()
                            )
                            .failure()
                    }
                    dataSourceSpecificJsonRetrievalStrategyProviders.none { strategyProvider ->
                        strategyProvider
                            .canProvideJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
                                dataSource!!.key
                            )
                    } -> {
                        DataSourceException(
                                DataSourceErrorResponse.STRATEGY_MISSING,
                                """no ${DataSourceSpecificJsonRetrievalStrategyProvider::class.qualifiedName} 
                                    |found that supports this type of data_source: 
                                    |[ actual: ${dataSource!!.key}  
                                    |]""".flatten()
                            )
                            .failure()
                    }
                    else -> {
                        Try.fromOption(
                                dataSourceSpecificJsonRetrievalStrategyProviders.firstOrNone {
                                    strategyProvider ->
                                    strategyProvider
                                        .canProvideJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
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
                provider: DataSourceSpecificJsonRetrievalStrategyProvider<SI>,
                dataSource: DataSource<*>,
                sourceVertices: PersistentSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
                parameterVertices:
                    PersistentSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>
            ): Try<SchematicPathBasedJsonRetrievalFunction> {
                // If already assessed as acceptable in earlier check, then this datasource must be
                // of this source_index type
                @Suppress("UNCHECKED_CAST")
                val typedDataSource: DataSource<SI> = dataSource as DataSource<SI>
                return provider.createSchematicPathBasedJsonRetrievalFunctionFor(
                    typedDataSource,
                    sourceVertices,
                    parameterVertices
                )
            }
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder(
            dataSourceSpecificJsonRetrievalStrategyProviders =
                dataSourceSpecificJsonRetrievalStrategyProviders
        )
    }
}
