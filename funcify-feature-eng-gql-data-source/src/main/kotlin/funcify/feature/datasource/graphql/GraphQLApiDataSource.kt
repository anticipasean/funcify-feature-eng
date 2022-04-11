package funcify.feature.datasource.graphql

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import graphql.schema.GraphQLSchema
import reactor.core.publisher.Mono


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

    fun executeQuery(query: String,
                     variables: Map<String, Any> = mapOf(),
                     operationName: String? = null): Mono<JsonNode>

}