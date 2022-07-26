package funcify.feature.schema.factory

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
internal interface MetamodelGraphCreationStrategyTemplate<CTX> {

    fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataSource<SI>, contextContainer: CTX): CTX

    fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
        attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
        dataSource: DataSource<SI>,
        contextContainer: CTX
    ): CTX

    fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
        lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        dataSource: DataSource<SI>,
        contextContainer: CTX
    ): CTX

    fun <SI : SourceIndex<SI>> createNewOrUpdateExistingSchematicVertex(
        dataSource: DataSource<SI>,
        sourcePath: SchematicPath,
        sourceIndex: SI,
        contextContainer: CTX
    ): CTX

    fun <SI : SourceIndex<SI>> fetchAliasesForDataSourceFromProvider(
        dataSource: DataSource<SI>,
        aliasProvider: DataSourceAttributeAliasProvider<SI>,
        contextContainer: CTX
    ): CTX

    fun <SI : SourceIndex<SI>> fetchLastUpdatedTemporalAttributesForDataSourceFromProvider(
        dataSource: DataSource<SI>,
        lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        contextContainer: CTX
    ): CTX

    fun addSchematicVertexGraphRemappingStrategy(
        strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
        contextContainer: CTX
    ): CTX

    fun applySchematicVertexGraphRemappingStrategy(
        schematicVertexGraphRemappingStrategy:
            SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
        contextContainer: CTX
    ): CTX
}
