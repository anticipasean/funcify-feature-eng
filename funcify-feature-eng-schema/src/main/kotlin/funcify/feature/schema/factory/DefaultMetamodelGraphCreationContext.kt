package funcify.feature.schema.factory

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.factory.MetamodelGraphCreationContext.Builder
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultMetamodelGraphCreationContext(
    override val schematicVertexFactory: SchematicVertexFactory,
    override val schematicVertexGraphRemappingStrategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
    override val aliasRegistry: AttributeAliasRegistry,
    override val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry,
    override val dataSourcesByName: PersistentMap<String, DataSource<*>> = persistentMapOf(),
    override val aliasProvidersByDataSourceName:
        PersistentMap<String, DataSourceAttributeAliasProvider<*>> =
        persistentMapOf(),
    override val lastUpdatedProvidersByDataSourceName:
        PersistentMap<String, DataSourceAttributeLastUpdatedProvider<*>> =
        persistentMapOf(),
    override val schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex> =
        persistentMapOf(),
    override val errors: PersistentList<Throwable> = persistentListOf()
) : MetamodelGraphCreationContext {

    companion object {
        internal class DefaultBuilder(
            private var schematicVertexFactory: SchematicVertexFactory,
            private var schematicVertexGraphRemappingStrategy:
                SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
            private var aliasRegistry: AttributeAliasRegistry,
            private var lastUpdatedTemporalAttributePathRegistry:
                LastUpdatedTemporalAttributePathRegistry,
            private var dataSourcesByName: PersistentMap.Builder<String, DataSource<*>>,
            private var aliasProvidersByDataSourceName:
                PersistentMap.Builder<String, DataSourceAttributeAliasProvider<*>>,
            private var lastUpdatedProvidersByDataSourceName:
                PersistentMap.Builder<String, DataSourceAttributeLastUpdatedProvider<*>>,
            private var schematicVerticesByPath:
                PersistentMap.Builder<SchematicPath, SchematicVertex>,
            private var errors: PersistentList.Builder<Throwable>
        ) : Builder {

            override fun schematicVertexFactory(
                schematicVertexFactory: SchematicVertexFactory
            ): Builder {
                this.schematicVertexFactory = schematicVertexFactory
                return this
            }

            override fun schematicVertexGraphRemappingStrategy(
                schematicVertexGraphRemappingStrategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
            ): Builder {
                this.schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy
                return this
            }

            override fun aliasRegistry(aliasRegistry: AttributeAliasRegistry): Builder {
                this.aliasRegistry = aliasRegistry
                return this
            }

            override fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataSource<SI>): Builder {
                this.dataSourcesByName.put(dataSource.name, dataSource)
                return this
            }

            override fun lastUpdatedTemporalAttributePathRegistry(
                lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry
            ): Builder {
                this.lastUpdatedTemporalAttributePathRegistry =
                    lastUpdatedTemporalAttributePathRegistry
                return this
            }

            override fun <SI : SourceIndex<SI>> addAliasProviderForDataSource(
                dataSource: DataSource<SI>,
                aliasProvider: DataSourceAttributeAliasProvider<SI>,
            ): Builder {
                this.aliasProvidersByDataSourceName.put(dataSource.name, aliasProvider)
                return this
            }

            override fun <SI : SourceIndex<SI>> addLastUpdatedProviderForDataSource(
                lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
                dataSource: DataSource<SI>,
            ): Builder {
                this.lastUpdatedProvidersByDataSourceName.put(dataSource.name, lastUpdatedProvider)
                return this
            }

            override fun addOrUpdateSchematicVertexAtPath(
                schematicPath: SchematicPath,
                schematicVertex: SchematicVertex,
            ): Builder {
                this.schematicVerticesByPath.put(schematicPath, schematicVertex)
                return this
            }

            override fun addError(error: Throwable): Builder {
                this.errors.add(error)
                return this
            }

            override fun build(): MetamodelGraphCreationContext {
                return DefaultMetamodelGraphCreationContext(
                    schematicVertexFactory = schematicVertexFactory,
                    schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy,
                    dataSourcesByName = dataSourcesByName.build(),
                    aliasProvidersByDataSourceName = aliasProvidersByDataSourceName.build(),
                    aliasRegistry = aliasRegistry,
                    lastUpdatedTemporalAttributePathRegistry =
                        lastUpdatedTemporalAttributePathRegistry,
                    lastUpdatedProvidersByDataSourceName =
                        lastUpdatedProvidersByDataSourceName.build(),
                    schematicVerticesByPath = schematicVerticesByPath.build(),
                    errors = errors.build()
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): MetamodelGraphCreationContext {
        val builder: Builder =
            DefaultBuilder(
                schematicVertexFactory = schematicVertexFactory,
                schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy,
                aliasRegistry = aliasRegistry,
                lastUpdatedTemporalAttributePathRegistry = lastUpdatedTemporalAttributePathRegistry,
                dataSourcesByName = dataSourcesByName.builder(),
                aliasProvidersByDataSourceName = aliasProvidersByDataSourceName.builder(),
                lastUpdatedProvidersByDataSourceName =
                    lastUpdatedProvidersByDataSourceName.builder(),
                schematicVerticesByPath = schematicVerticesByPath.builder(),
                errors = errors.builder()
            )
        return transformer.invoke(builder).build()
    }
}
