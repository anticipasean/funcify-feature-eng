package funcify.feature.datasource.graphql

import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import graphql.schema.GraphQLSchema


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataSource {

    val name: String

    val graphQLApiService: GraphQLApiService

    val graphQLSourceSchema: GraphQLSchema

    val graphQLSourceMetamodel: GraphQLSourceMetamodel

}