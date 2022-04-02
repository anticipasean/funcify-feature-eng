package funcify.feature.tools.container.graph

import arrow.core.None
import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.some
import arrow.core.toOption
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import java.util.Deque
import java.util.LinkedList
import java.util.Spliterator
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.function.Consumer
import java.util.stream.Stream


/**
 * Use of this spliterator instead of performing within the method body of a dfs method on a graph
 * enables callers to perform other work on the earlier tuples encountered while the next tuple is determined
 * rather than making the caller wait for the full stream of tuples to be created before having any tuples
 * to process
 * @author smccarron
 * @created 4/2/22
 */
internal class DepthFirstSearchSpliterator<P, V, E>(private val inputPath: P,
                                                    private val vertices: ImmutableMap<P, V>,
                                                    private val edges: ImmutableMap<Pair<P, P>, E>,
                                                    private val pathConnections: ImmutableMap<P, ImmutableSet<P>>) : Spliterator<Tuple5<V, P, E, P, V>> {

    companion object {

        private const val DEFAULT_CHARACTERISTICS: Int = ORDERED and NONNULL

    }

    private val pathsToVisitBuffer: Deque<P> = LinkedList<P>().apply { add(inputPath) }

    private val visitedPathsSet: MutableSet<P> = mutableSetOf()

    private val childToParentPathRelationships: MutableMap<P, P> = mutableMapOf()

    private var expended: Boolean = false

    private val childNodesNotVisitedSelector: (P) -> Iterable<P> = { pathParam: P ->
        pathParam.toOption()
                .map { p: P ->
                    pathConnections.getOrDefault(p,
                                                 persistentSetOf())
                }
                .map { pairs: ImmutableSet<P> -> pairs.stream() }
                .getOrElse { Stream.empty() }
                .filter { cp -> !visitedPathsSet.contains(cp) && cp != pathParam }
                .toList()
                .asReversed()
    }

    override fun tryAdvance(action: Consumer<in Tuple5<V, P, E, P, V>>?): Boolean {
        if (action == null || expended) {
            return false
        }
        /**
         * Get first path within paths_to_visit buffer
         * If this spliterator has not been marked _expended_, then
         * there should at least be one path within the buffer
         * In the case of the first call to this spliterator, that path
         * should be the input_path
         */
        var nextPathToVisit: P = pathsToVisitBuffer.pollFirst()
        /**
         * Exclude any paths that have, since the time they were added to the buffer,
         * been visited
         */
        while (visitedPathsSet.contains(nextPathToVisit) && pathsToVisitBuffer.isNotEmpty()) {
            nextPathToVisit = pathsToVisitBuffer.pollFirst()
        }
        /**
         * If all possible paths to visit have been exhausted and the current path has already
         * been visited, mark spliterator as _expended_
         */
        if (visitedPathsSet.contains(nextPathToVisit) && pathsToVisitBuffer.isEmpty()) {
            expended = true
            return false
        }
        /**
         * Mark the current path as visited before assessing whether it has child paths
         * to visit
         */
        visitedPathsSet.add(nextPathToVisit)
        /**
         * Add paths to visit to the front of the queue in the reverse order from which
         * they were discovered: e.g. { current_queue: [ 4, 5 ] => child_paths_discovered: [ 1, 2, 3 ]
         * => child_paths_reversed: [ 3, 2, 1 ] => updated_queue: [ 3, 2, 1, 4, 5 ] }
         * Store each child's path relationship to a parent path
         */
        childNodesNotVisitedSelector.invoke(nextPathToVisit)
                .forEach { childPath: P ->
                    pathsToVisitBuffer.offerFirst(childPath)
                    childToParentPathRelationships[childPath] = nextPathToVisit
                }
        /**
         * If no paths_to_visit are available, we cannot peek ahead and determine the
         * link from the next_path_to_visit to its child_path, and therefore, cannot
         * determine the edge or child vertex
         * => Mark _expended_
         */
        if (pathsToVisitBuffer.isEmpty()) {
            expended = true
            return false
        }
        /**
         * Peek at the first path in the paths_to_visit queue, the next child_path for the
         * current next_path_to_visit, and determine its connecting edge and vertex
         * If such a connecting edge and vertex cannot be determined, the vertices and/or edges passed to this
         * spliterator as input are missing expected relationships and the spliterator should be
         * marked _expended_
         */
        val childPath: P = pathsToVisitBuffer.peekFirst()
        val fullPathConnectionForChildPath: Option<Tuple5<V, P, E, P, V>> = getFullPathConnectionForChildPath(childPath)
        return if (fullPathConnectionForChildPath.isDefined()) {
            action.accept(fullPathConnectionForChildPath.orNull()!!)
            true
        } else {
            expended = true
            false
        }

    }

    private fun getFullPathConnectionForChildPath(childPath: P): Option<Tuple5<V, P, E, P, V>> {
        return childToParentPathRelationships[childPath].toOption()
                .flatMap { parentPath: P ->
                    val parentVertex: V? = vertices[parentPath]
                    val childVertex: V? = vertices[childPath]
                    if (parentVertex != null && childVertex != null) {
                        val edge: E? = edges[parentPath to childPath]
                        if (edge != null) {
                            Tuple5(parentVertex,
                                   parentPath,
                                   edge,
                                   childPath,
                                   childVertex).some()
                        } else {
                            None
                        }
                    } else {
                        None
                    }
                }
    }

    /**
     * Cannot split since size of full path connections is not known in advance
     * @return null
     */
    override fun trySplit(): Spliterator<Tuple5<V, P, E, P, V>>? {
        return null
    }

    override fun estimateSize(): Long { // size unknown in advance of starting
        return Long.MAX_VALUE
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }

}