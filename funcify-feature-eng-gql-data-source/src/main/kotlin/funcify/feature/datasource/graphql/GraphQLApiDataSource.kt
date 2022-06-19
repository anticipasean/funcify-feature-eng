package funcify.feature.datasource.graphql

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataSource : DataSource<GraphQLSourceIndex> {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.GRAPHQL_API

    override val name: String

    val graphQLApiService: GraphQLApiService

    val graphQLSourceSchema: GraphQLSchema

    override val sourceMetamodel: SourceMetamodel<GraphQLSourceIndex>
}
