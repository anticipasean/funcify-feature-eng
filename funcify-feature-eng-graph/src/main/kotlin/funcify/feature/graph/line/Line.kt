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

    /** Return a new [Line] wherein component2 becomes component1 and vice versa */
    fun swap(): Line<P>
}
