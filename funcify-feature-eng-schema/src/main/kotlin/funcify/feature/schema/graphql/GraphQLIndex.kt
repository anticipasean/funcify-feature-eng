package funcify.feature.schema.graphql

import funcify.feature.schema.SchematicPath
import graphql.schema.GraphQLType


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface GraphQLIndex {

    val path: SchematicPath

    val name: String

    val type: GraphQLType

}