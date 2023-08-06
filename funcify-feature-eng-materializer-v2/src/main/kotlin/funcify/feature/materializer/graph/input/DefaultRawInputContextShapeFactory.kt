package funcify.feature.materializer.graph.input

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.TreePath
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet

/**
 * @author smccarron
 * @created 2023-08-06
 */
internal object DefaultRawInputContextShapeFactory {

    internal class DefaultTabularShape(override val columnSet: ImmutableSet<String>) :
        RawInputContextShape.Tabular {}

    internal class DefaultTreeShape(override val treePathSet: ImmutableSet<TreePath>) :
        RawInputContextShape.Tree {

        override val fieldNames: ImmutableSet<String> by lazy {
            treePathSet
                .asSequence()
                .map { tp: TreePath ->
                    tp.pathSegments.lastOrNone().filterIsInstance<NameSegment>().map {
                        ns: NameSegment ->
                        ns.name
                    }
                }
                .flatMapOptions()
                .toPersistentSet()
        }
    }

    fun createTabularShape(columnSet: ImmutableSet<String>): RawInputContextShape.Tabular {
        return DefaultTabularShape(columnSet = columnSet)
    }

    fun createTreeShape(treePathSet: ImmutableSet<TreePath>): RawInputContextShape.Tree {
        return DefaultTreeShape(treePathSet = treePathSet)
    }
}
