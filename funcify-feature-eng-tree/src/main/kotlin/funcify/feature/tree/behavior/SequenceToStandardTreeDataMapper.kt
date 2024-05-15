package funcify.feature.tree.behavior

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.lastOrNone
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardEmptyTreeData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardNonEmptyTreeData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.path.IndexSegment
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.TreePath
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal object SequenceToStandardTreeDataMapper {

    fun <V> createStandardTreeDataFromSequence(
        sequence: Sequence<Pair<TreePath, V>>
    ): StandardTreeData<V> {
        return sequence
            .groupBy { p: Pair<TreePath, V> -> p.first.pathSegments.size }
            .asSequence()
            .sortedWith(
                Comparator.comparing(
                    Map.Entry<Int, List<Pair<TreePath, V>>>::key,
                    Comparator.reverseOrder()
                )
            )
            .fold(
                persistentMapOf<TreePath, StandardNonEmptyTreeData<V>>(),
                ::foldValuesByPathAtLevelIntoParentNodesOfNextLevel
            )
            .let { rootLevelNodes: PersistentMap<TreePath, StandardNonEmptyTreeData<V>> ->
                rootLevelNodes.getOrNone(TreePath.getRootPath()).getOrElse {
                    StandardEmptyTreeData.getInstance<V>()
                }
            }
    }

    private fun <V> foldValuesByPathAtLevelIntoParentNodesOfNextLevel(
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardNonEmptyTreeData<V>>,
        valuesByPathAtLevel: Map.Entry<Int, List<Pair<TreePath, V>>>
    ): PersistentMap<TreePath, StandardNonEmptyTreeData<V>> {
        val (level: Int, valuesByPath: List<Pair<TreePath, V>>) = valuesByPathAtLevel
        return when (level) {
            0 -> {
                valuesByPath
                    .lastOrNone()
                    .map(
                        createNewLeafOrUpdateObjectOrArrayBranchFromChildrenFromPreviousLevel(
                            childrenFromPreviousLevel
                        )
                    )
                    .map(::persistentMapOf)
                    .getOrElse { persistentMapOf() }
            }
            else -> {
                valuesByPath
                    .asSequence()
                    .groupBy { (p: TreePath, _: V) ->
                        p.parent().getOrElse { TreePath.getRootPath() }
                    }
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
                    .fold(persistentMapOf<TreePath, StandardNonEmptyTreeData<V>>()) { pm, pair ->
                        pm.put(pair.first, pair.second)
                    }
            }
        }
    }

    private fun <V> createStandardArrayBranchDataForParentsAtLevel(
        parentPath: TreePath,
        children: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardNonEmptyTreeData<V>>,
    ): StandardArrayBranchData<V> {
        return children
            .asSequence()
            .filter { (ctp: TreePath, _: V) ->
                ctp.lastSegment().filterIsInstance<IndexSegment>().isDefined()
            }
            .sortedBy(Pair<TreePath, V>::first)
            .map(
                createNewLeafOrUpdateObjectOrArrayBranchFromChildrenFromPreviousLevel(
                    childrenFromPreviousLevel
                )
            )
            .fold(0 to persistentListOf<StandardNonEmptyTreeData<V>>()) {
                (count: Int, cpl: PersistentList<StandardNonEmptyTreeData<V>>),
                p: Pair<TreePath, StandardNonEmptyTreeData<V>> ->
                // TODO: Create top level and sub type builders, populating in folds and building
                // after head value obtained
                (count +
                    when (val td: StandardNonEmptyTreeData<V> = p.second) {
                        is StandardLeafData<V> -> 1
                        is StandardArrayBranchData<V> -> 1 + td.subNodeCount
                        is StandardObjectBranchData<V> -> 1 + td.subNodeCount
                    }) to cpl.add(p.second)
            }
            .let { (subNodeCount: Int, indexed: PersistentList<StandardNonEmptyTreeData<V>>) ->
                StandardArrayBranchData<V>(
                    subNodeCount = subNodeCount,
                    value = null,
                    children = indexed
                )
            }
    }

    private fun <V> createStandardObjectBranchDataForParentsAtLevel(
        parentPath: TreePath,
        children: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardNonEmptyTreeData<V>>,
    ): StandardObjectBranchData<V> {
        return children
            .asSequence()
            .filter { (ctp: TreePath, _: V) ->
                ctp.lastSegment().filterIsInstance<NameSegment>().isDefined()
            }
            // TODO: Make determination as to whether sorting should be done on object field names
            .sortedBy(Pair<TreePath, V>::first)
            .map(
                createNewLeafOrUpdateObjectOrArrayBranchFromChildrenFromPreviousLevel(
                    childrenFromPreviousLevel
                )
            )
            .fold(0 to persistentMapOf<String, StandardNonEmptyTreeData<V>>()) {
                (count: Int, cpm: PersistentMap<String, StandardNonEmptyTreeData<V>>),
                p: Pair<TreePath, StandardNonEmptyTreeData<V>> ->
                (count +
                    when (val td: StandardNonEmptyTreeData<V> = p.second) {
                        is StandardLeafData<V> -> 1
                        is StandardArrayBranchData<V> -> 1 + td.subNodeCount
                        is StandardObjectBranchData<V> -> 1 + td.subNodeCount
                    }) to
                    cpm.put(
                        p.first.lastSegment().filterIsInstance<NameSegment>().orNull()!!.name,
                        p.second
                    )
            }
            .let { (subNodeCount: Int, named: PersistentMap<String, StandardNonEmptyTreeData<V>>) ->
                StandardObjectBranchData<V>(
                    subNodeCount = subNodeCount,
                    value = null,
                    children = named
                )
            }
    }

    private fun <V> createNewLeafOrUpdateObjectOrArrayBranchFromChildrenFromPreviousLevel(
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardNonEmptyTreeData<V>>
    ): (Pair<TreePath, V>) -> Pair<TreePath, StandardNonEmptyTreeData<V>> {
        return { (ctp: TreePath, cv: V) ->
            childrenFromPreviousLevel
                .getOrNone(ctp)
                .mapNotNull { cstd: StandardNonEmptyTreeData<V> ->
                    when (cstd) {
                        is StandardArrayBranchData<V> -> {
                            ctp to
                                StandardArrayBranchData<V>(
                                    subNodeCount = cstd.subNodeCount,
                                    value = cv,
                                    children = cstd.children
                                )
                        }
                        is StandardObjectBranchData<V> -> {
                            ctp to
                                StandardObjectBranchData<V>(
                                    subNodeCount = cstd.subNodeCount,
                                    value = cv,
                                    children = cstd.children
                                )
                        }
                        else -> {
                            null
                        }
                    }
                }
                .getOrElse { ctp to StandardLeafData<V>(cv) }
        }
    }
}
