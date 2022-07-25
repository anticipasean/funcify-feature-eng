package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface MetamodelGraph {

    val dataSourcesByKey: ImmutableMap<DataSource.Key<*>, DataSource<*>>

    val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge>

    val attributeAliasRegistry: AttributeAliasRegistry

    val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry

    interface Builder {

        fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataSource<SI>): Builder

        fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
            attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
            dataSource: DataSource<SI>
        ): Builder

        fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
            lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
            dataSource: DataSource<SI>
        ): Builder

        fun build(): Deferred<MetamodelGraph>
    }
}
