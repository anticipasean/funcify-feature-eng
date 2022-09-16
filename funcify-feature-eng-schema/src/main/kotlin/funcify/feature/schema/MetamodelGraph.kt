package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.identifier.DataSourceEntityIdentifiersProvider
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterContainerTypeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

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

    val entityRegistry: EntityRegistry

    val sourceAttributeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<SourceAttributeVertex>>

    val sourceContainerTypeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<SourceContainerTypeVertex>>

    val parameterAttributeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<ParameterAttributeVertex>>

    val parameterContainerTypeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<ParameterContainerTypeVertex>>

    val sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<SourceAttributeVertex>>

    val parameterAttributeVerticesWithParentTypeAttributeQualifiedNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<ParameterAttributeVertex>>

    val parameterAttributeVerticesBySourceAttributeVertexPaths:
        ImmutableMap<SchematicPath, ImmutableSet<ParameterAttributeVertex>>

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

        fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
            entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
            dataSource: DataSource<SI>
        ): Builder

        fun addRemappingStrategyForPostProcessingSchematicVertices(
            strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
        ): Builder

        fun build(): Mono<MetamodelGraph>
    }
}
