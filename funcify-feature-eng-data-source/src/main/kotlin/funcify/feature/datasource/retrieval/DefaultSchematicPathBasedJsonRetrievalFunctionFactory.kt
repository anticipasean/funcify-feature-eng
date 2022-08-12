package funcify.feature.datasource.retrieval

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunction.Builder
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultSchematicPathBasedJsonRetrievalFunctionFactory(
    override val dataSourceSpecificJsonRetrievalStrategies:
        ImmutableSet<DataSourceSpecificJsonRetrievalStrategy<*>> =
        persistentSetOf()
) : SchematicPathBasedJsonRetrievalFunctionFactory {

    companion object {

        internal data class DefaultSchematicPathBasedJsonRetrievalFunction(
            internal val dataSourceSpecificJsonRetrievalStrategy:
                DataSourceSpecificJsonRetrievalStrategy<*>,
            override val dataSource: DataSource<*> =
                dataSourceSpecificJsonRetrievalStrategy.dataSource,
            internal val parameterVertices:
                ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                dataSourceSpecificJsonRetrievalStrategy.parameterVertices,
            internal val sourceVertices:
                ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>> =
                dataSourceSpecificJsonRetrievalStrategy.sourceVertices
        ) : SchematicPathBasedJsonRetrievalFunction {

            override val parameterPaths: ImmutableSet<SchematicPath> by lazy {
                parameterVertices
                    .asSequence()
                    .map { jvOrlv -> jvOrlv.fold({ pjv -> pjv.path }, { plv -> plv.path }) }
                    .toPersistentSet()
            }

            override val sourcePaths: ImmutableSet<SchematicPath> by lazy {
                sourceVertices
                    .asSequence()
                    .map { jvOrlv -> jvOrlv.fold({ sjv -> sjv.path }, { slv -> slv.path }) }
                    .toPersistentSet()
            }

            override fun invoke(
                valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
            ): Deferred<ImmutableMap<SchematicPath, JsonNode>> {
                return dataSourceSpecificJsonRetrievalStrategy.invoke(valuesByParameterPaths)
            }
        }

        internal class DefaultBuilder(
            private val dataSourceSpecificJsonRetrievalStrategyByDataSourceKey:
                ImmutableMap<DataSource.Key<*>, DataSourceSpecificJsonRetrievalStrategy<*>> =
                persistentMapOf(),
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
                TODO()
            }
        }
    }

    private val dataSourceSpecificJsonRetrievalStrategyByDataSourceKey:
        ImmutableMap<DataSource.Key<*>, DataSourceSpecificJsonRetrievalStrategy<*>> by lazy {
        dataSourceSpecificJsonRetrievalStrategies.fold(persistentMapOf()) { pm, strategy ->
            pm.put(strategy.dataSourceKey, strategy)
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder(
            dataSourceSpecificJsonRetrievalStrategyByDataSourceKey =
                dataSourceSpecificJsonRetrievalStrategyByDataSourceKey
        )
    }
}
