package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.stream.Stream

internal interface DirectedGraphBehavior<DWT> : GraphBehavior<DWT> {

    override fun <P> line(firstOrSource: P, secondOrDestination: P): Line<P> {
        return DirectedLine.of(firstOrSource, secondOrDestination)
    }

    fun <P, V, E> successorsAsStream(
        container: GraphData<DWT, P, V, E>,
        point: P
    ): Stream<out Pair<P, V>>

    fun <P, V, E> predecessorVerticesAsStream(
        container: GraphData<DWT, P, V, E>,
        point: P
    ): Stream<out Pair<P, V>>

    fun <P, V, E> edgesFromPointAsStream(
        container: GraphData<DWT, P, V, E>,
        point: P
    ): Stream<out Pair<Line<P>, E>>

    fun <P, V, E> edgesToPointAsStream(
        container: GraphData<DWT, P, V, E>,
        point: P
    ): Stream<out Pair<Line<P>, E>>
}
