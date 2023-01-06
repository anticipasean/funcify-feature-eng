package funcify.feature.graph.line

/**
 * @param P
 * - "Point" Type
 * @author smccarron
 * @created 2023-01-03
 */
sealed interface Line<out P> {

    operator fun component1(): P

    operator fun component2(): P

}
