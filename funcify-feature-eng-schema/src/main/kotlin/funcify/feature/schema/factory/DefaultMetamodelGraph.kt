package funcify.feature.schema.factory

import arrow.core.filterIsInstance
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterContainerTypeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentSet

/**
 *
 * @author smccarron
 * @created 4/2/22
 */
internal data class DefaultMetamodelGraph(
    override val dataSourcesByKey: PersistentMap<DataSource.Key<*>, DataSource<*>>,
    override val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> =
        PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
    override val attributeAliasRegistry: AttributeAliasRegistry,
    override val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry
) : MetamodelGraph {
    companion object {}

    override val sourceAttributeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<SourceAttributeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<SourceAttributeVertex>()
            .map { sav: SourceAttributeVertex ->
                sav.compositeAttribute.conventionalName.qualifiedForm to sav
            }
            .groupBy({ (n, _) -> n }, { (_, sav) -> sav })
            .asSequence()
            .map { (name, srcAttrs) -> name to srcAttrs.toPersistentSet() }
            .reducePairsToPersistentMap()
    }

    override val sourceContainerTypeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<SourceContainerTypeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<SourceContainerTypeVertex>()
            .map { sct: SourceContainerTypeVertex ->
                sct.compositeContainerType.conventionalName.qualifiedForm to sct
            }
            .groupBy({ (n, _) -> n }, { (_, srcContTypes) -> srcContTypes })
            .asSequence()
            .map { (name, srcContTypes) -> name to srcContTypes.toPersistentSet() }
            .reducePairsToPersistentMap()
    }

    override val parameterAttributeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<ParameterAttributeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<ParameterAttributeVertex>()
            .map { pav: ParameterAttributeVertex ->
                pav.compositeParameterAttribute.conventionalName.qualifiedForm to pav
            }
            .groupBy({ (n, _) -> n }, { (_, paramAttrs) -> paramAttrs })
            .asSequence()
            .map { (name, paramAttrs) -> name to paramAttrs.toPersistentSet() }
            .reducePairsToPersistentMap()
    }
    override val parameterContainerTypeVerticesByQualifiedName:
        ImmutableMap<String, ImmutableSet<ParameterContainerTypeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<ParameterContainerTypeVertex>()
            .map { pct: ParameterContainerTypeVertex ->
                pct.compositeParameterContainerType.conventionalName.qualifiedForm to pct
            }
            .groupBy({ (n, _) -> n }, { (_, paramContTypes) -> paramContTypes })
            .asSequence()
            .map { (name, paramContTypes) -> name to paramContTypes.toPersistentSet() }
            .reducePairsToPersistentMap()
    }
    override val sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<SourceAttributeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<SourceAttributeVertex>()
            .map { sav: SourceAttributeVertex ->
                sav.path.getParentPath().flatMap { pp ->
                    pathBasedGraph
                        .getVertex(pp)
                        .filterIsInstance<SourceContainerTypeVertex>()
                        .map { sct: SourceContainerTypeVertex ->
                            (sct.compositeContainerType.conventionalName.qualifiedForm to
                                sav.compositeAttribute.conventionalName.qualifiedForm) to sav
                        }
                }
            }
            .flatMapOptions()
            .reducePairsToPersistentSetValueMap()
    }
    override val parameterAttributeVerticesWithParentTypeAttributeQualifiedNamePair:
        ImmutableMap<Pair<String, String>, ImmutableSet<ParameterAttributeVertex>> by lazy {
        pathBasedGraph.verticesByPath.values
            .asSequence()
            .filterIsInstance<ParameterAttributeVertex>()
            .map { pav: ParameterAttributeVertex ->
                pav.path.getParentPath().flatMap { pp ->
                    pathBasedGraph
                        .getVertex(pp)
                        .filterIsInstance<ParameterContainerTypeVertex>()
                        .map { pct: ParameterContainerTypeVertex ->
                            (pct.compositeParameterContainerType.conventionalName.qualifiedForm to
                                pav.compositeParameterAttribute.conventionalName.qualifiedForm) to
                                pav
                        }
                }
            }
            .flatMapOptions()
            .reducePairsToPersistentSetValueMap()
    }
}
