package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.ParameterIndex
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface GraphQLParameterIndex : GraphQLSourceIndex, ParameterIndex<GraphQLSourceIndex> {

    override val sourcePath: SchematicPath

    override val name: ConventionalName

    override val dataType: GraphQLInputType
}
