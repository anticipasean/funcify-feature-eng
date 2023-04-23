package funcify.feature.tree.behavior

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.lastOrNone
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.path.IndexSegment
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.TreePath
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal class StreamToStandardTreeDataMapper<V> :
    (Stream<out Pair<TreePath, V>>) -> StandardTreeData<V> {

    override fun invoke(stream: Stream<out Pair<TreePath, V>>): StandardTreeData<V> {
        return stream
            .collect(Collectors.groupingBy { p: Pair<TreePath, V> -> p.first.pathSegments.size })
            .entries
            .stream()
            .sorted(
                Comparator.comparing(
                    Map.Entry<Int, List<Pair<TreePath, V>>>::key,
                    Comparator.reverseOrder()
                )
            )
            .reduce(
                persistentMapOf<TreePath, StandardTreeData<V>>(),
                ::foldLevelValuesIntoChildDataNodesOfNextLevel,
                PersistentMap<TreePath, StandardTreeData<V>>::putAll
            )
            .let { children -> createRootTreeDataFromTopLevelValueNodes(children) }
    }

    private fun foldLevelValuesIntoChildDataNodesOfNextLevel(
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
        dataByLevel: Map.Entry<Int, List<Pair<TreePath, V>>>
    ): PersistentMap<TreePath, StandardTreeData<V>> {
        val (level: Int, nextValuesSet: List<Pair<TreePath, V>>) = dataByLevel
        return when {
            level == 0 -> {
                childrenFromPreviousLevel
            }
            else -> {
                nextValuesSet
                    .asSequence()
                    .groupBy { (p: TreePath, _: V) ->
                        p.parent().getOrElse { TreePath.getRootPath() }
                    }
                    .entries
                    .asSequence()
                    .filterNot { (parentPath: TreePath, _: List<Pair<TreePath, V>>) ->
                        TreePath.getRootPath() == parentPath || parentPath.pathSegments.size == 0
                    }
                    .map { (parentPath: TreePath, childrenForParentPath: List<Pair<TreePath, V>>) ->
                        when {
                            childrenForParentPath
                                .firstOrNone()
                                .flatMap { (ctp: TreePath, _: V) ->
                                    ctp.pathSegments.lastOrNone().filterIsInstance<IndexSegment>()
                                }
                                .isDefined() -> {
                                parentPath to
                                    createStandardArrayBranchDataForParentsAtLevel(
                                        childrenForParentPath,
                                        childrenFromPreviousLevel
                                    )
                            }
                            else -> {
                                parentPath to
                                    createStandardObjectBranchDataForParentsAtLevel(
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
        }
    }

    private fun createStandardArrayBranchDataForParentsAtLevel(
        parents: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
    ): StandardArrayBranchData<V> {
        return parents
            .asSequence()
            .filter { (ctp: TreePath, v: V) ->
                ctp.pathSegments.lastOrNone().filterIsInstance<IndexSegment>().isDefined()
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
            .let { children: PersistentList<StandardTreeData<V>> ->
                StandardArrayBranchData(value = null, children = children)
            }
    }

    private fun createStandardObjectBranchDataForParentsAtLevel(
        parents: List<Pair<TreePath, V>>,
        childrenFromPreviousLevel: PersistentMap<TreePath, StandardTreeData<V>>,
    ): StandardObjectBranchData<V> {
        return parents
            .asSequence()
            .filter { (ctp: TreePath, v: V) ->
                ctp.pathSegments.lastOrNone().filterIsInstance<NameSegment>().isDefined()
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
            .let { cpm: PersistentMap<String, StandardTreeData<V>> ->
                StandardObjectBranchData(value = null, children = cpm)
            }
    }

    private fun createRootTreeDataFromTopLevelValueNodes(
        children: PersistentMap<TreePath, StandardTreeData<V>>
    ): StandardTreeData<V> {
        return when {
            children.isEmpty() -> {
                StandardTreeData.getRoot()
            }
            children.keys
                .firstOrNone()
                .flatMap { tp: TreePath ->
                    tp.pathSegments.firstOrNone().filterIsInstance<IndexSegment>()
                }
                .isDefined() -> {
                children
                    .asSequence()
                    .sortedBy(Map.Entry<TreePath, StandardTreeData<V>>::key)
                    .flatMap { (key: TreePath, value: StandardTreeData<V>) ->
                        key.pathSegments
                            .firstOrNone()
                            .filterIsInstance<IndexSegment>()
                            .map { _: IndexSegment -> sequenceOf(value) }
                            .getOrElse { emptySequence() }
                    }
                    .fold(persistentListOf<StandardTreeData<V>>()) { std, c -> std.add(c) }
                    .let { stds: PersistentList<StandardTreeData<V>> ->
                        StandardArrayBranchData<V>(null, stds)
                    }
            }
            else -> {
                children
                    .asSequence()
                    .sortedBy(Map.Entry<TreePath, StandardTreeData<V>>::key)
                    .flatMap { (key: TreePath, value: StandardTreeData<V>) ->
                        key.pathSegments
                            .firstOrNone()
                            .filterIsInstance<NameSegment>()
                            .map { ns: NameSegment -> sequenceOf(ns.name to value) }
                            .getOrElse { emptySequence() }
                    }
                    .fold(persistentMapOf<String, StandardTreeData<V>>()) { std, p ->
                        std.put(p.first, p.second)
                    }
                    .let { stdsByName: PersistentMap<String, StandardTreeData<V>> ->
                        StandardObjectBranchData<V>(null, stdsByName)
                    }
            }
        }
    }
}
