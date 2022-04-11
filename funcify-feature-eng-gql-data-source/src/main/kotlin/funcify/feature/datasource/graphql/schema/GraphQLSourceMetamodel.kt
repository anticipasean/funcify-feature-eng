package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet


/**
 *
 * @author smccarron
 * @created 4/9/22
 */
data class GraphQLSourceMetamodel(override val sourceIndicesByPath: PersistentMap<SchematicPath, PersistentSet<GraphQLSourceIndex>>) : SourceMetamodel<GraphQLSourceIndex> {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.GRAPHQL_API

}
