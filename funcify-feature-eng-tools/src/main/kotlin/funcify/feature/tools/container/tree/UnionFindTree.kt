package funcify.feature.tools.container.tree

import arrow.core.Option
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 4/1/22
 */
interface UnionFindTree<P> {

    companion object {

        fun <P> empty(): UnionFindTree<P> {
            return DefaultUnionFindTree<P>()
        }

    }

    val parents: ImmutableMap<P, P>

    val ranks: ImmutableMap<P, Int>

    val treeSizes: ImmutableMap<P, Int>

    fun hasPath(path: P): Boolean {
        return parents.containsKey(path)
    }

    fun add(path: P): UnionFindTree<P>

    /**
     * Find the root_path for this input path within the parents map
     * @return a pair consisting of the root_path if one exists and
     * the current or _updated_ version of UnionFindTree
     */
    fun find(path: P): Pair<Option<P>, UnionFindTree<P>>

    fun union(path1: P,
              path2: P): UnionFindTree<P>

    fun getTreeMemberRankedSetsByRepresentativeRootPath(): ImmutableMap<P, ImmutableSet<Pair<P, Int>>>

}