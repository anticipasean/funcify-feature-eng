package funcify.feature.materializer.schema

import funcify.feature.schema.Metamodel
import graphql.schema.GraphQLSchema
import java.time.Instant

/**
 * @author smccarron
 * @created 2022-08-04
 */
interface MaterializationMetamodel {

    val created: Instant

    val metamodel: Metamodel

    val materializationGraphQLSchema: GraphQLSchema
}
