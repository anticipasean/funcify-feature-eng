package funcify.feature.graphql.schema

import funcify.feature.schema.SchematicPath
import graphql.schema.GraphQLType


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
data class GraphQLContainerType(override val canonicalPath: SchematicPath,
                                override val name: String,
                                override val type: GraphQLType) : GraphQLIndex {


}