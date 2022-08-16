package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunction
import funcify.feature.json.JsonMapper
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableSet

internal class DefaultGraphQLDataSourceJsonRetrievalStrategyProvider(
    private val jsonMapper: JsonMapper
) : GraphQLDataSourceJsonRetrievalStrategyProvider {

    override fun canProvideJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean {
        return dataSourceKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class)
    }

    override fun createSchematicPathBasedJsonRetrievalFunctionFor(
        dataSource: DataSource<GraphQLSourceIndex>,
        sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
        parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    ): Try<SchematicPathBasedJsonRetrievalFunction> {
        return Try.attempt {
            DefaultGraphQLDataSourceJsonRetrievalStrategy(
                jsonMapper,
                dataSource as GraphQLApiDataSource,
                parameterVertices,
                sourceVertices
            )
        }
    }
}
