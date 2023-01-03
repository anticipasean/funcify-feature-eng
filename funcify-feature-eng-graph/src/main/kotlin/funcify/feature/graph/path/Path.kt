package funcify.feature.graph.path

/**
 * @param P - "Point" Type
 * @author smccarron
 * @created 2023-01-03
 */
interface Path<out P> {

    val first: P

    val second: P

}
