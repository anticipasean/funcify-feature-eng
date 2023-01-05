package funcify.feature.graph

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
interface PersistentGraphFactory {

    fun <B : GraphBuilder<B>> builder(): B

}
