package funcify.feature.materializer.schema

import funcify.feature.schema.MetamodelGraph
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLSchema

interface MaterializationGraphQLSchemaFactory {

    fun createGraphQLSchemaFromMetamodelGraph(metamodelGraph: MetamodelGraph): Try<GraphQLSchema>

}
