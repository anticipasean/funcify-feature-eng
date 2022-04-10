package funcify.feature.datasource.graphql.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceIndex
import graphql.schema.GraphQLType


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface GraphQLSourceIndex : SourceIndex {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.GRAPHQL_API
    override val sourcePath: SchematicPath

    override val name: ConventionalName

    val type: GraphQLType

}