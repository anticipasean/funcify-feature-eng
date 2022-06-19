package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLOutputType

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface GraphQLSourceIndex : SourceIndex<GraphQLSourceIndex> {

    override val sourcePath: SchematicPath

    override val name: ConventionalName

    val dataType: GraphQLOutputType
}
