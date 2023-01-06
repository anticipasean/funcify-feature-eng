package funcify.feature.graph.behavior

import funcify.feature.graph.line.Line
import funcify.feature.graph.line.UndirectedLine

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface UndirectedGraphBehavior<DWT> : GraphBehavior<DWT> {

    override fun <P> line(firstOrSource: P, secondOrDestination: P): Line<P> {
        return UndirectedLine.of(firstOrSource, secondOrDestination)
    }
}
