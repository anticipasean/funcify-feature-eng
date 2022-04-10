package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.datasource.SourceAttribute
import graphql.schema.GraphQLType


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
data class GraphQLSourceAttribute(override val sourcePath: SchematicPath,
                                  override val name: ConventionalName,
                                  override val type: GraphQLType) : GraphQLSourceIndex,
                                                                    SourceAttribute {


}