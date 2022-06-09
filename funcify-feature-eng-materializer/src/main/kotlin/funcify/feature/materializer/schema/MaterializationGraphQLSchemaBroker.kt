package funcify.feature.materializer.schema

import funcify.feature.tools.container.deferred.Deferred
import graphql.schema.GraphQLSchema

interface MaterializationGraphQLSchemaBroker {

    fun pushNewMaterializationSchema(materializationSchema: GraphQLSchema)

    fun fetchLatestMaterializationSchema(): Deferred<GraphQLSchema>

}
