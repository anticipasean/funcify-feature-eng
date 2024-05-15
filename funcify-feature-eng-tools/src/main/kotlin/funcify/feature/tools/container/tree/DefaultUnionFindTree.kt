package funcify.feature.tools.container.tree

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.extensions.OptionExtensions.recurse
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap

/**
 * @author smccarron
 * @created 3/31/22
 */
internal data class DefaultUnionFindTree<P>(
    override val parents: PersistentMap<P, P> = persistentMapOf<P, P>(),
    override val ranks: PersistentMap<P, Int> = persistentMapOf<P, Int>(),
    override val treeSizes: PersistentMap<P, Int> = persistentMapOf<P, Int>()
) : UnionFindTree<P> {

    companion object {

        private fun <T1, T2, R> Pair<T1, T2>.fold(function: (T1, T2) -> R): R {
            return function.invoke(this.first, this.second)
        }
    }

    override fun add(path: P): DefaultUnionFindTree<P> {
        return if (parents.containsKey(path)) {
            this
        } else {
            copy(
                parents = parents.put(path, path),
                ranks = ranks.put(path, 0),
                treeSizes = treeSizes.put(path, 1)
            )
        }
    }

    /**
     * Find the root_path for this input path within the parents map
     *
     * @return a pair consisting of the root_path if one exists and the current or _updated_ version
     *   of {@link UnionFindTreeCreator}
     */
    override fun find(path: P): Pair<Option<P>, UnionFindTree<P>> {
        /**
         * Case 0: Path does not have a listing in parents => No _update_ (=> new instance with
         * different parameters since immutable) to the {@link UnionFindTreeCreator} should be made
         * root_path is empty in option returned
         */
        if (!parents.containsKey(path)) {
            return None to this
        }
        return path
            .some()
            .flatMap { currentPath: P ->
                /**
                 * Find the root_path (<= the path in parent_listings for which the only current
                 * parent listing is itself) for this input_current_path
                 */
                Option(currentPath)
                    .recurse { cp: P ->
                        val parentOpt: Option<P> = parents[cp].toOption()
                        if (parentOpt.filter { pp -> pp == cp }.isDefined()) {
                            /**
                             * Case 1: The iteration_current_path is equal to its parent in the
                             * parent path listings meaning it is a root_path => Map root to current
                             * path
                             */
                            /**
                             * Case 1: The iteration_current_path is equal to its parent in the
                             * parent path listings meaning it is a root_path => Map root to current
                             * path
                             */
                            cp.right().some<Either<P, P>>()
                        } else {
                            /**
                             * Case 2: The iteration_current_path is not equal to its parent path
                             * meaning it is not a root_path => Make the parent path the next
                             * iteration_current_path and find the root for this path
                             */
                            /**
                             * Case 2: The iteration_current_path is not equal to its parent path
                             * meaning it is not a root_path => Make the parent path the next
                             * iteration_current_path and find the root for this path
                             */
                            parentOpt.map { pp -> pp.left() }
                        }
                    }
                    .map { root ->
                        /** Associate this root_path with the input_current_path */
                        Pair(root, currentPath)
                    }
            }
            .flatMap { rootAndCurrentPath: Pair<P, P> ->
                /**
                 * Parent listings tail recursive update for Path Compression => Set start value to
                 * ( input_current_path, current_parent_listings )
                 */
                Option(Pair(rootAndCurrentPath.second, parents))
                    .recurse { currentPathAndParents: Pair<P, PersistentMap<P, P>> ->
                        /**
                         * Case 1: Update current_parent_listings if iteration_current_path is not
                         * equal to root_path => Set iteration_current_path to its parent_path and
                         * make this parent_path the parent of the root_path
                         */
                        /**
                         * Case 1: Update current_parent_listings if iteration_current_path is not
                         * equal to root_path => Set iteration_current_path to its parent_path and
                         * make this parent_path the parent of the root_path
                         */
                        if (currentPathAndParents.first != rootAndCurrentPath.first) {
                            currentPathAndParents.second[currentPathAndParents.first]
                                .toOption()
                                .map { parent ->
                                    (parent to
                                            currentPathAndParents.second.put(
                                                currentPathAndParents.first,
                                                rootAndCurrentPath.first
                                            ))
                                        .left<Pair<P, PersistentMap<P, P>>>()
                                }
                        } else {
                            /**
                             * Case 2: Do not update current_parent_listings if
                             * iteration_current_path is equal to root_path => Return the
                             * current_parent_listings (which may be different than the starting
                             * version of current_parent_listings)
                             */
                            /**
                             * Case 2: Do not update current_parent_listings if
                             * iteration_current_path is equal to root_path => Return the
                             * current_parent_listings (which may be different than the starting
                             * version of current_parent_listings)
                             */
                            (currentPathAndParents.second)
                                .right()
                                .some<Either<Pair<P, PersistentMap<P, P>>, PersistentMap<P, P>>>()
                        }
                    }
                    .map { updatedParents: PersistentMap<P, P> ->
                        /**
                         * Associate root_path with potentially _updated_ current_parent_listings
                         */
                        Pair(rootAndCurrentPath.first, updatedParents)
                    }
            }
            .let { rootAndUpdatedParents: Option<Pair<P, PersistentMap<P, P>>> ->
                /** Return root and _updated_ {@link UnionFindTreeCreator} instance to caller */
                rootAndUpdatedParents
                    .map { rootAndParents ->
                        rootAndParents.first.some() to copy(parents = rootAndParents.second)
                    }
                    .getOrElse { None to this }
            }
    }

    override fun union(path1: P, path2: P): UnionFindTree<P> {
        /** Do not proceed if either path does not have a parent listing */
        if (!parents.containsKey(path1) || !parents.containsKey(path2)) {
            return this
        }
        /** Find root path for first path */
        val rootAndUnionFind1: Pair<Option<P>, UnionFindTree<P>> = find(path1)

        /**
         * Since the find op performs path compression, use the resulting union-find to find the
         * root path of the second path
         */
        val rootAndUnionFind2: Pair<Option<P>, DefaultUnionFindTree<P>> =
            rootAndUnionFind1.second.find(path2).fold { pOpt, uft ->
                Pair(
                    pOpt,
                    uft as? DefaultUnionFindTree<P>
                        ?: DefaultUnionFindTree(
                            parents = uft.parents.toPersistentMap(),
                            ranks = uft.ranks.toPersistentMap(),
                            treeSizes = uft.treeSizes.toPersistentMap()
                        )
                )
            }
        /**
         * If neither path for some reason is defined with a root, then return the latest version of
         * the union-find
         */
        if (!rootAndUnionFind1.first.isDefined() || !rootAndUnionFind2.first.isDefined()) {
            return rootAndUnionFind2.second
        }
        val rootpath1: P = rootAndUnionFind1.first.orNull()!!
        val rootpath2: P = rootAndUnionFind2.first.orNull()!!
        val currentUnionFind: DefaultUnionFindTree<P> = rootAndUnionFind2.second
        return when {
            /**
             * rootpath 1 or 2 is/are not properly defined in ranks and/or tree size maps any
             * further union operations would not follow the expected algorithm sequence => return
             * the _updated_ UnionFindTree from the second find operation
             */
            sequenceOf(rootpath1, rootpath2).any { path: P ->
                !currentUnionFind.ranks.containsKey(path) ||
                    !currentUnionFind.treeSizes.containsKey(path)
            } -> {
                currentUnionFind
            }
            /**
             * path1 and path2 share the same root, so they belong to the same set => return the
             * _updated_ UnionFindTree from the second find operation
             */
            rootpath1 == rootpath2 -> {
                currentUnionFind
            }
            /** rank of root_path 1 is less than that of root_path 2 */
            currentUnionFind.ranks[rootpath1]!! < currentUnionFind.ranks[rootpath2]!! -> {
                /**
                 * Make root 1 parent of root 2 and augment tree size of root 1 by size of tree of
                 * root 2 => Favor the smaller rank for determining parents
                 */
                DefaultUnionFindTree(
                    parents = currentUnionFind.parents.put(rootpath2, rootpath1),
                    ranks = currentUnionFind.ranks,
                    treeSizes =
                        currentUnionFind.treeSizes.put(
                            rootpath1,
                            currentUnionFind.treeSizes.getOrDefault(rootpath1, 1) +
                                currentUnionFind.treeSizes.getOrDefault(rootpath2, 1)
                        )
                )
            }
            /** rank of root_path 1 is greater than that of root_path 2 */
            currentUnionFind.ranks[rootpath1]!! > currentUnionFind.ranks[rootpath2]!! -> {
                /**
                 * Make root_path 2 parent of root_path 2 and augment tree size of root 2 by size of
                 * tree of root 1 => Favor the smaller rank for determining parents
                 */
                DefaultUnionFindTree(
                    parents = currentUnionFind.parents.put(rootpath1, rootpath2),
                    ranks = currentUnionFind.ranks,
                    treeSizes =
                        currentUnionFind.treeSizes.put(
                            rootpath2,
                            currentUnionFind.treeSizes.getOrDefault(rootpath2, 1) +
                                currentUnionFind.treeSizes.getOrDefault(rootpath1, 1)
                        )
                )
            }
            /**
             * rank of root_path 1 is equal to that of root_path 2 but tree size of root_path 2 is
             * greater than that of root_path 1
             */
            currentUnionFind.treeSizes[rootpath1]!! < currentUnionFind.treeSizes[rootpath2]!! -> {
                /**
                 * Given the tie in ranks, make root_path 2 parent of root 1, augment rank of root 1
                 * by that of root_path 2 + 1, augment tree size of root 2 by tree size of
                 * root_paths 1 and 2 => Favor the larger tree size when determining parents
                 */
                DefaultUnionFindTree(
                    parents = currentUnionFind.parents.put(rootpath1, rootpath2),
                    ranks =
                        currentUnionFind.ranks.put(
                            rootpath1,
                            currentUnionFind.ranks.getOrDefault(rootpath2, 0) + 1
                        ),
                    treeSizes =
                        currentUnionFind.treeSizes.put(
                            rootpath2,
                            currentUnionFind.treeSizes.getOrDefault(rootpath1, 1) +
                                currentUnionFind.treeSizes.getOrDefault(rootpath2, 1)
                        )
                )
            }
            /**
             * rank of root_path 1 is equal to that of root_path 2 but the tree size of root_path 1
             * is greater than that of root_path 2
             */
            currentUnionFind.treeSizes[rootpath1]!! > currentUnionFind.treeSizes[rootpath2]!! -> {
                /**
                 * Given the tie in ranks, make root 1 parent of root 2, augment rank of root 2 by
                 * rank of root 1 + 1, augment tree size of root 1 by tree size of root 1 and that
                 * of root 2 => Favor the larger tree size when determining parents
                 */
                DefaultUnionFindTree(
                    parents = currentUnionFind.parents.put(rootpath2, rootpath1),
                    ranks =
                        currentUnionFind.ranks.put(
                            rootpath2,
                            currentUnionFind.ranks.getOrDefault(rootpath1, 0) + 1
                        ),
                    treeSizes =
                        currentUnionFind.treeSizes.put(
                            rootpath1,
                            currentUnionFind.treeSizes.getOrDefault(rootpath1, 1) +
                                currentUnionFind.treeSizes.getOrDefault(rootpath2, 1)
                        )
                )
            }
            else -> {
                /**
                 * both the ranks and tree sizes of root_paths 1 and 2 are equal => Arbitrarily make
                 * root_path 2 a child to root_path 1, augment rank of root_path 2 with that of
                 * root_path 1 + 1, and augment tree size of root_path 1 by both its current and
                 * root_path 2 's tree size
                 */
                DefaultUnionFindTree(
                    parents = currentUnionFind.parents.put(rootpath2, rootpath1),
                    ranks =
                        currentUnionFind.ranks.put(
                            rootpath2,
                            currentUnionFind.ranks.getOrDefault(rootpath1, 0) + 1
                        ),
                    treeSizes =
                        currentUnionFind.treeSizes.put(
                            rootpath1,
                            currentUnionFind.treeSizes.getOrDefault(rootpath1, 1) +
                                currentUnionFind.treeSizes.getOrDefault(rootpath2, 1)
                        )
                )
            }
        }
    }

    override fun getTreeMemberRankedSetsByRepresentativeRootPath():
        ImmutableMap<P, ImmutableSet<Pair<P, Int>>> {
        return parents
            .asSequence()
            .partition { entry: Map.Entry<P, P> -> entry.key == entry.value }
            .fold { roots, children ->
                children
                    .asSequence()
                    .map { child: Map.Entry<P, P> -> child.key to ranks.getOrDefault(child.key, 0) }
                    .sortedBy { pair: Pair<P, Int> -> pair.second }
                    .fold(
                        roots
                            .asSequence()
                            .map { entry: Map.Entry<P, P> -> entry.key }
                            .fold(
                                persistentMapOf(),
                                { acc: PersistentMap<P, PersistentSet<Pair<P, Int>>>, p: P ->
                                    acc.put(p, persistentSetOf<Pair<P, Int>>())
                                }
                            )
                    ) { acc: PersistentMap<P, PersistentSet<Pair<P, Int>>>, pair: Pair<P, Int> ->
                        find(pair.first)
                            .first
                            .fold(
                                { acc },
                                { cr ->
                                    acc.put(cr, acc.getOrDefault(cr, persistentSetOf()).add(pair))
                                }
                            )
                    }
            }
    }

    override fun toString(): String {
        return getTreeMemberRankedSetsByRepresentativeRootPath()
            .asSequence()
            .map { entry: Map.Entry<P, ImmutableSet<Pair<P, Int>>> ->
                "    { path: ${entry.key}, rank: ${ranks[entry.key]}, tree_size: ${treeSizes[entry.key]}, children: ${
                        entry.value.asSequence()
                                .map { pair: Pair<P, Int> ->
                                    "( path: ${pair.first}, rank: ${pair.second}, tree_size: ${treeSizes[pair.first]} )"
                                }
                                .joinToString(separator = ", ",
                                              prefix = "{ ",
                                              postfix = " }")
                    } }"
            }
            .joinToString(separator = ",\n", prefix = "{\n", postfix = "\n}")
    }
}
