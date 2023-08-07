package funcify.feature.materializer.schema.path

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.some
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object SourceAttributeDataSourceAncestorPathFinder :
        (MaterializationMetamodel, GQLOperationPath) -> GQLOperationPath {

    private val dataSourceAncestorPathMemoizer:
                (MaterializationMetamodel, GQLOperationPath) -> GQLOperationPath by lazy {
        val cache: ConcurrentMap<Pair<Instant, GQLOperationPath>, GQLOperationPath> = ConcurrentHashMap();
        { mm: MaterializationMetamodel, path: GQLOperationPath ->
            cache.computeIfAbsent(mm.created to path, dataSourceAncestorPathCalculator(mm))
        }
    }

    override fun invoke(
        materializationMetamodel: MaterializationMetamodel,
        path: GQLOperationPath
    ): GQLOperationPath {
        return dataSourceAncestorPathMemoizer(materializationMetamodel, path)
    }

    private fun dataSourceAncestorPathCalculator(
        materializationMetamodel: MaterializationMetamodel
    ): (Pair<Instant, GQLOperationPath>) -> GQLOperationPath {
        return { (materializationMetamodelCreated: Instant, path: GQLOperationPath) ->
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
        currentPath: GQLOperationPath,
        dataSourceKey: DataElementSource.Key<*>,
        metamodelGraph: MetamodelGraph
    ): GQLOperationPath {
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
