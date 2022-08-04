package funcify.feature.materializer.schema

import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 2022-08-04
 */
interface MaterializationMetamodel {

    val metamodelGraph: MetamodelGraph

    val materializationGraphQLSchema: GraphQLSchema

}
