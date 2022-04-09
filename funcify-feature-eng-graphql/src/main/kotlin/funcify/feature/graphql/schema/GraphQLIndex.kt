package funcify.feature.graphql.schema

import funcify.feature.datasource.RawDataSourceType
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceIndex
import graphql.schema.GraphQLType


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface GraphQLIndex : SourceIndex {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.GRAPHQL
    override val canonicalPath: SchematicPath

    override val name: String

    val type: GraphQLType

}