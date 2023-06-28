package funcify.feature.schema.factory

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.some
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.identifier.DataSourceEntityIdentifiersProvider
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.factory.MetamodelGraphCreationContext.Builder
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterContainerTypeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultMetamodelGraphCreationContext(
    override val schematicVertexFactory: SchematicVertexFactory,
    override val schematicVertexGraphRemappingStrategy:
        SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
    override val aliasRegistry: AttributeAliasRegistry,
    override val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry,
    override val entityRegistry: EntityRegistry,
    override val dataSourcesByName: PersistentMap<String, DataElementSource<*>> = persistentMapOf(),
    override val aliasProvidersByDataSourceName:
        PersistentMap<String, DataSourceAttributeAliasProvider<*>> =
        persistentMapOf(),
    override val lastUpdatedProvidersByDataSourceName:
        PersistentMap<String, DataSourceAttributeLastUpdatedProvider<*>> =
        persistentMapOf(),
    override val entityIdentifiersProvidersByDataSourceName:
        PersistentMap<String, DataSourceEntityIdentifiersProvider<*>> =
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
            private var entityRegistry: EntityRegistry,
            private var dataSourcesByName: PersistentMap.Builder<String, DataElementSource<*>>,
            private var aliasProvidersByDataSourceName:
                PersistentMap.Builder<String, DataSourceAttributeAliasProvider<*>>,
            private var lastUpdatedProvidersByDataSourceName:
                PersistentMap.Builder<String, DataSourceAttributeLastUpdatedProvider<*>>,
            private var entityIdentifiersProvidersByDataSourceName:
                PersistentMap.Builder<String, DataSourceEntityIdentifiersProvider<*>>,
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
                schematicVertexGraphRemappingStrategy:
                    SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
            ): Builder {
                this.schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy
                return this
            }

            override fun aliasRegistry(aliasRegistry: AttributeAliasRegistry): Builder {
                this.aliasRegistry = aliasRegistry
                return this
            }

            override fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataElementSource<SI>): Builder {
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

            override fun entityRegistry(entityRegistry: EntityRegistry): Builder {
                this.entityRegistry = entityRegistry
                return this
            }

            override fun <SI : SourceIndex<SI>> addAliasProviderForDataSource(
                dataSource: DataElementSource<SI>,
                aliasProvider: DataSourceAttributeAliasProvider<SI>,
            ): Builder {
                this.aliasProvidersByDataSourceName.put(dataSource.name, aliasProvider)
                return this
            }

            override fun <SI : SourceIndex<SI>> addLastUpdatedProviderForDataSource(
                lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
                dataSource: DataElementSource<SI>,
            ): Builder {
                this.lastUpdatedProvidersByDataSourceName.put(dataSource.name, lastUpdatedProvider)
                return this
            }

            override fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
                entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
                dataSource: DataElementSource<SI>,
            ): Builder {
                this.entityIdentifiersProvidersByDataSourceName.put(
                    dataSource.name,
                    entityIdentifiersProvider
                )
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

            override fun removeSchematicVertexAtPath(schematicPath: SchematicPath): Builder {
                this.schematicVerticesByPath.remove(schematicPath)
                return this
            }

            override fun build(): MetamodelGraphCreationContext {
                return DefaultMetamodelGraphCreationContext(
                    schematicVertexFactory = schematicVertexFactory,
                    schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy,
                    dataSourcesByName = dataSourcesByName.build(),
                    aliasRegistry = aliasRegistry,
                    lastUpdatedTemporalAttributePathRegistry =
                        lastUpdatedTemporalAttributePathRegistry,
                    entityRegistry = entityRegistry,
                    aliasProvidersByDataSourceName = aliasProvidersByDataSourceName.build(),
                    lastUpdatedProvidersByDataSourceName =
                        lastUpdatedProvidersByDataSourceName.build(),
                    entityIdentifiersProvidersByDataSourceName =
                        entityIdentifiersProvidersByDataSourceName.build(),
                    schematicVerticesByPath = schematicVerticesByPath.build(),
                    errors = errors.build()
                )
            }
        }
    }

    override val childAttributeVerticesByParentTypeAndChildAttributeNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<SchematicVertex>> by lazy {
        schematicVerticesByPath.entries
            .parallelStream()
            .map { (p, v) ->
                when (v) {
                    is SourceAttributeVertex -> {
                        p.getParentPath()
                            .flatMap { pp -> schematicVerticesByPath.getOrNone(pp) }
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .map { sct ->
                                sct.compositeContainerType.conventionalName.qualifiedForm
                            }
                            .zip(v.compositeAttribute.conventionalName.qualifiedForm.some())
                            .map { parentTypeChildAttrNamePair -> parentTypeChildAttrNamePair to v }
                    }
                    is ParameterAttributeVertex -> {
                        p.getParentPath()
                            .flatMap { pp -> schematicVerticesByPath.getOrNone(pp) }
                            .filterIsInstance<ParameterContainerTypeVertex>()
                            .map { pct ->
                                pct.compositeParameterContainerType.conventionalName.qualifiedForm
                            }
                            .zip(
                                v.compositeParameterAttribute.conventionalName.qualifiedForm.some()
                            )
                            .map { parentTypeChildAttrNamePair -> parentTypeChildAttrNamePair to v }
                    }
                    else -> {
                        none()
                    }
                }
            }
            .flatMapOptions()
            .reducePairsToPersistentSetValueMap()
    }

    override fun update(transformer: Builder.() -> Builder): MetamodelGraphCreationContext {
        val builder: Builder =
            DefaultBuilder(
                schematicVertexFactory = schematicVertexFactory,
                schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy,
                aliasRegistry = aliasRegistry,
                lastUpdatedTemporalAttributePathRegistry = lastUpdatedTemporalAttributePathRegistry,
                entityRegistry = entityRegistry,
                dataSourcesByName = dataSourcesByName.builder(),
                aliasProvidersByDataSourceName = aliasProvidersByDataSourceName.builder(),
                lastUpdatedProvidersByDataSourceName =
                    lastUpdatedProvidersByDataSourceName.builder(),
                entityIdentifiersProvidersByDataSourceName =
                    entityIdentifiersProvidersByDataSourceName.builder(),
                schematicVerticesByPath = schematicVerticesByPath.builder(),
                errors = errors.builder()
            )
        return transformer.invoke(builder).build()
    }
}
