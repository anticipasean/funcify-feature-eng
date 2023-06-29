package funcify.feature.materializer.schema.path

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.some
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object SourceAttributeDataSourceAncestorPathFinder :
    (MaterializationMetamodel, SchematicPath) -> SchematicPath {

    private val dataSourceAncestorPathMemoizer:
        (MaterializationMetamodel, SchematicPath) -> SchematicPath by lazy {
        val cache: ConcurrentMap<Pair<Instant, SchematicPath>, SchematicPath> = ConcurrentHashMap();
        { mm: MaterializationMetamodel, path: SchematicPath ->
            cache.computeIfAbsent(mm.created to path, dataSourceAncestorPathCalculator(mm))
        }
    }

    override fun invoke(
        materializationMetamodel: MaterializationMetamodel,
        path: SchematicPath
    ): SchematicPath {
        return dataSourceAncestorPathMemoizer(materializationMetamodel, path)
    }

    private fun dataSourceAncestorPathCalculator(
        materializationMetamodel: MaterializationMetamodel
    ): (Pair<Instant, SchematicPath>) -> SchematicPath {
        return { (materializationMetamodelCreated: Instant, path: SchematicPath) ->
            materializationMetamodel.metamodelGraph.pathBasedGraph
                .getVertex(path)
                .filterIsInstance<SourceAttributeVertex>()
                .map { sav: SourceAttributeVertex ->
                    sav.compositeAttribute.getSourceAttributeByDataSource().keys.asSequence()
                }
                .fold(::emptySequence, ::identity)
                .map { dsKey ->
                    findAncestorOrKeepCurrentWithSameDataSource(
                        path,
                        dsKey,
                        materializationMetamodel.metamodelGraph
                    )
                }
                .firstOrNone { sp -> sp != path }
                .getOrElse { path }
        }
    }

    private fun findAncestorOrKeepCurrentWithSameDataSource(
        currentPath: SchematicPath,
        dataSourceKey: DataElementSource.Key<*>,
        metamodelGraph: MetamodelGraph
    ): SchematicPath {
        return currentPath
            .some()
            .recurse { p ->
                if (
                    p.getParentPath()
                        .flatMap { pp -> metamodelGraph.pathBasedGraph.getVertex(pp) }
                        .filterIsInstance<SourceAttributeVertex>()
                        .map(SourceAttributeVertex::compositeAttribute)
                        .map(CompositeSourceAttribute::getSourceAttributeByDataSource)
                        .fold(::emptyMap, ::identity)
                        .containsKey(dataSourceKey)
                ) {
                    p.getParentPath().map { pp -> pp.left() }
                } else {
                    p.right().some()
                }
            }
            .getOrElse { currentPath }
    }
}
