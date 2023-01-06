package funcify.feature.graph.data

import funcify.feature.graph.data.StandardDirectedGraphData.Companion.StandardDirectedGraphWT
import funcify.feature.graph.line.DirectedLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class StandardDirectedGraphData<P, V, E>(
    val verticesByPoint: PersistentMap<P, V>,
    val edgesByLine: PersistentMap<DirectedLine<P>, E>
) : GraphData<StandardDirectedGraphWT, P, V, E> {

    companion object {
        enum class StandardDirectedGraphWT

        fun <P, V, E> narrow(
            container: GraphData<StandardDirectedGraphWT, P, V, E>
        ): StandardDirectedGraphData<P, V, E> {
            return container as StandardDirectedGraphData<P, V, E>
        }

        fun <P, V, E> GraphData<StandardDirectedGraphWT, P, V, E>.narrowed():
            StandardDirectedGraphData<P, V, E> {
            return StandardDirectedGraphData.narrow(this)
        }

        private val EMPTY: StandardDirectedGraphData<Any, Any, Any> =
            StandardDirectedGraphData<Any, Any, Any>(persistentMapOf(), persistentMapOf())

        fun <P, V, E> empty(): StandardDirectedGraphData<P, V, E> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as StandardDirectedGraphData<P, V, E>
        }
    }

    val outgoingLines: PersistentMap<P, PersistentSet<P>> by lazy {
        edgesByLine.entries
            .parallelStream()
            .map { (d: DirectedLine<P>, _: E) -> d }
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
        edgesByLine.entries
            .parallelStream()
            .map { (d: DirectedLine<P>, _: E) -> d }
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
