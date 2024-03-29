package funcify.feature.schema.factory

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.identifier.DataSourceEntityIdentifiersProvider
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
interface MetamodelGraphCreationContext {

    val schematicVertexFactory: SchematicVertexFactory

    val schematicVertexGraphRemappingStrategy:
        SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>

    val dataSourcesByName: ImmutableMap<String, DataSource<*>>

    val aliasProvidersByDataSourceName: ImmutableMap<String, DataSourceAttributeAliasProvider<*>>

    val aliasRegistry: AttributeAliasRegistry

    val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry

    val lastUpdatedProvidersByDataSourceName:
        ImmutableMap<String, DataSourceAttributeLastUpdatedProvider<*>>

    val entityRegistry: EntityRegistry

    val entityIdentifiersProvidersByDataSourceName:
        ImmutableMap<String, DataSourceEntityIdentifiersProvider<*>>

    val schematicVerticesByPath: ImmutableMap<SchematicPath, SchematicVertex>

    val childAttributeVerticesByParentTypeAndChildAttributeNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<SchematicVertex>>

    val errors: ImmutableList<Throwable>

    fun update(transformer: Builder.() -> Builder): MetamodelGraphCreationContext

    interface Builder {

        fun schematicVertexFactory(schematicVertexFactory: SchematicVertexFactory): Builder

        fun schematicVertexGraphRemappingStrategy(
            schematicVertexGraphRemappingStrategy:
                SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
        ): Builder

        fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataSource<SI>): Builder

        fun aliasRegistry(aliasRegistry: AttributeAliasRegistry): Builder

        fun <SI : SourceIndex<SI>> addAliasProviderForDataSource(
            dataSource: DataSource<SI>,
            aliasProvider: DataSourceAttributeAliasProvider<SI>
        ): Builder

        fun lastUpdatedTemporalAttributePathRegistry(
            lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry
        ): Builder

        fun <SI : SourceIndex<SI>> addLastUpdatedProviderForDataSource(
            lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
            dataSource: DataSource<SI>
        ): Builder

        fun entityRegistry(entityRegistry: EntityRegistry): Builder

        fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
            entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
            dataSource: DataSource<SI>
        ): Builder

        fun addOrUpdateSchematicVertexAtPath(
            schematicPath: SchematicPath,
            schematicVertex: SchematicVertex
        ): Builder

        fun removeSchematicVertexAtPath(schematicPath: SchematicPath): Builder

        fun addError(error: Throwable): Builder

        fun build(): MetamodelGraphCreationContext
    }
}
