package funcify.feature.graph.data

import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.line.DirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class ParallelizableEdgeDirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesSetByLine: PersistentMap<DirectedLine<P>, PersistentSet<E>>
) : GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {

    companion object {
        enum class ParallelizableEdgeDirectedGraphWT

        fun <P, V, E> narrow(
            container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
        ): ParallelizableEdgeDirectedGraphData<P, V, E> {
            return container as ParallelizableEdgeDirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>.narrowed():
            ParallelizableEdgeDirectedGraphData<P, V, E> {
            return ParallelizableEdgeDirectedGraphData.narrow(this)
        }

        private val EMPTY: ParallelizableEdgeDirectedGraphData<Any, Any, Any> =
            ParallelizableEdgeDirectedGraphData<Any, Any, Any>(persistentMapOf(), persistentMapOf())

        fun <P, V, E> empty(): ParallelizableEdgeDirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as ParallelizableEdgeDirectedGraphData<P, V, E>
        }
    }

    val outgoingLines: PersistentMap<P, PersistentSet<P>> by lazy {
        edgesSetByLine.entries
            .parallelStream()
            .map { (d: DirectedLine<P>, _: PersistentSet<E>) -> d }
            .reduce(
                persistentMapOf<P, PersistentSet<P>>(),
                { pm: PersistentMap<P, PersistentSet<P>>, d: DirectedLine<P> ->
                    pm.put(
                        d.sourcePoint,
                        pm.getOrElse(d.sourcePoint) { -> persistentSetOf() }.add(d.destinationPoint)
                    )
                },
                { pm1, pm2 ->
                    val pm1Builder = pm1.builder()
                    pm2.forEach { (p: P, dp: PersistentSet<P>) ->
                        pm1Builder[p] = pm1Builder.getOrElse(p) { -> persistentSetOf() }.addAll(dp)
                    }
                    pm1Builder.build()
                }
            )
    }

    val incomingLines: PersistentMap<P, PersistentSet<P>> by lazy {
        edgesSetByLine.entries
            .parallelStream()
            .map { (d: DirectedLine<P>, _: PersistentSet<E>) -> d }
            .reduce(
                persistentMapOf<P, PersistentSet<P>>(),
                { pm: PersistentMap<P, PersistentSet<P>>, d: DirectedLine<P> ->
                    pm.put(
                        d.destinationPoint,
                        pm.getOrElse(d.destinationPoint) { -> persistentSetOf() }.add(d.sourcePoint)
                    )
                },
                { pm1, pm2 ->
                    val pm1Builder = pm1.builder()
                    pm2.forEach { (p: P, dp: PersistentSet<P>) ->
                        pm1Builder[p] = pm1Builder.getOrElse(p) { -> persistentSetOf() }.addAll(dp)
                    }
                    pm1Builder.build()
                }
            )
    }
}
