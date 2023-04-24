package funcify.feature.tree.behavior

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.path.IndexSegment
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.TreePath
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal object StreamToStandardTreeDataMapper {

    fun <V> createStandardTreeDataFromStream(
        stream: Stream<out Pair<TreePath, V>>
    ): StandardTreeData<V> {
        return stream
            .asSequence()
            .filterNot { p: Pair<TreePath, V> -> p.first.pathSegments.size == 0 }
            .groupBy { p: Pair<TreePath, V> -> p.first.pathSegments.size }
            .entries
            .asSequence()
            .sortedWith(
                Comparator.comparing(
                    Map.Entry<Int, List<Pair<TreePath, V>>>::key,
                    Comparator.reverseOrder()
                )
            )
            .fold(
                persistentMapOf<TreePath, StandardTreeData<V>>(),
                ::foldValuesByPathAtLevelIntoParentNodesOfNextLevel
            )
            .let { rootLevelNodes: PersistentMap<TreePath, StandardTreeData<V>> ->
                rootLevelNodes.getOrNone(TreePath.getRootPath()).getOrElse {
                    StandardTreeData.getRoot()
                }
            }
    }

    private fun <V> foldValuesByPathAtLevelIntoParentNodesOfNextLevel(
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
        dataByLevel: Map.Entry<Int, List<Pair<TreePath, V>>>
    ): PersistentMap<TreePath, StandardTreeData<V>> {
        val (level: Int, valuesByPath: List<Pair<TreePath, V>>) = dataByLevel
        return valuesByPath
            .asSequence()
            .groupBy { (p: TreePath, _: V) -> p.parent().getOrElse { TreePath.getRootPath() } }
            .entries
            .asSequence()
            .map { (parentPath: TreePath, childrenForParentPath: List<Pair<TreePath, V>>) ->
                when {
                    childrenForParentPath
                        .firstOrNone()
                        .flatMap { (ctp: TreePath, _: V) ->
                            ctp.lastSegment().filterIsInstance<IndexSegment>()
                        }
                        .isDefined() -> {
                        parentPath to
                            createStandardArrayBranchDataForParentsAtLevel(
                                parentPath,
                                childrenForParentPath,
                                childrenFromPreviousLevel
                            )
                    }
                    else -> {
                        parentPath to
                            createStandardObjectBranchDataForParentsAtLevel(
                                parentPath,
                                childrenForParentPath,
                                childrenFromPreviousLevel
                            )
                    }
                }
            }
            .fold(persistentMapOf<TreePath, StandardTreeData<V>>()) { pm, pair ->
                pm.put(pair.first, pair.second)
            }
    }

    private fun <V> createStandardArrayBranchDataForParentsAtLevel(
        parentPath: TreePath,
        children: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
    ): StandardArrayBranchData<V> {
        return children
            .asSequence()
            .filter { (ctp: TreePath, _: V) ->
                ctp.lastSegment().filterIsInstance<IndexSegment>().isDefined()
            }
            .sortedBy(Pair<TreePath, V>::first)
            .mapNotNull { (ctp: TreePath, v: V) ->
                childrenFromPreviousLevel
                    .getOrNone(ctp)
                    .fold(
                        { ctp to StandardLeafData<V>(v) },
                        { cstd: StandardTreeData<V> ->
                            when (cstd) {
                                is StandardArrayBranchData -> {
                                    ctp to StandardArrayBranchData<V>(v, cstd.children)
                                }
                                is StandardObjectBranchData -> {
                                    ctp to StandardObjectBranchData<V>(v, cstd.children)
                                }
                                else -> {
                                    null
                                }
                            }
                        }
                    )
            }
            .fold(persistentListOf<StandardTreeData<V>>()) { cpl, p -> cpl.add(p.second) }
            .let { indexedChildren: PersistentList<StandardTreeData<V>> ->
                StandardArrayBranchData(value = null, children = indexedChildren)
            }
    }

    private fun <V> createStandardObjectBranchDataForParentsAtLevel(
        parentPath: TreePath,
        children: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
    ): StandardObjectBranchData<V> {
        return children
            .asSequence()
            .filter { (ctp: TreePath, _: V) ->
                ctp.lastSegment().filterIsInstance<NameSegment>().isDefined()
            }
            .sortedBy(Pair<TreePath, V>::first)
            .mapNotNull { (ctp: TreePath, v: V) ->
                childrenFromPreviousLevel
                    .getOrNone(ctp)
                    .fold(
                        { ctp to StandardLeafData<V>(v) },
                        { cstd: StandardTreeData<V> ->
                            when (cstd) {
                                is StandardArrayBranchData -> {
                                    ctp to StandardArrayBranchData<V>(v, cstd.children)
                                }
                                is StandardObjectBranchData -> {
                                    ctp to StandardObjectBranchData<V>(v, cstd.children)
                                }
                                else -> {
                                    null
                                }
                            }
                        }
                    )
            }
            .fold(persistentMapOf<String, StandardTreeData<V>>()) { cpm, p ->
                cpm.put(
                    p.first.lastSegment().filterIsInstance<NameSegment>().orNull()!!.name,
                    p.second
                )
            }
            .let { namedChildren: PersistentMap<String, StandardTreeData<V>> ->
                StandardObjectBranchData(value = null, children = namedChildren)
            }
    }
}
