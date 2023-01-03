package funcify.feature.graph.path

/**
 *
 * @author smccarron
 * @created 2023-01-03
 */
interface DirectedPath<out P> : Path<P> {

    val source: P
        get() = first

    val destination: P
        get() = second
}
