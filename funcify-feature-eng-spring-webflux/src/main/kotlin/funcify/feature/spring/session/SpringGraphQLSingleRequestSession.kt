package funcify.feature.spring.session

import arrow.core.Option
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
interface SpringGraphQLSingleRequestSession : GraphQLSingleRequestSession {

    override val materializationSchema: GraphQLSchema

    override val metamodelGraph: MetamodelGraph

    override val rawGraphQLRequest: RawGraphQLRequest

    override val serializedGraphQLResponse: Option<SerializedGraphQLResponse>
}
