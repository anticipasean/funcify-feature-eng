package funcify.feature.schema

/**
 * @author smccarron
 * @created 2/20/22
 */
interface MetamodelGraph {
    /*

    val dataSourcesByKey: ImmutableMap<DataElementSource.Key<*>, DataElementSource<*>>

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

        fun <SI : SourceIndex<SI>> addDataSource(dataSource: DataElementSource<SI>): Builder

        fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
            attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
            dataSource: DataElementSource<SI>
        ): Builder

        fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
            lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
            dataSource: DataElementSource<SI>
        ): Builder

        fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
            entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
            dataSource: DataElementSource<SI>
        ): Builder

        fun addRemappingStrategyForPostProcessingSchematicVertices(
            strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
        ): Builder

        fun build(): Mono<MetamodelGraph>
    }

      */
}
