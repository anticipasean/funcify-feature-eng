package funcify.feature.datasource.graphql

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.RawSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataElementSource : DataElementSource<GraphQLSourceIndex> {

    override val sourceType: SourceType
        get() = RawSourceType.GRAPHQL_API

    override val name: String

    val graphQLApiService: GraphQLApiService

    val graphQLSourceSchema: GraphQLSchema

    override val sourceMetamodel: SourceMetamodel<GraphQLSourceIndex>
}
