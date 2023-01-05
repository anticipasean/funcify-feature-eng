package funcify.feature.graph.builder

import funcify.feature.graph.GraphBuilder
import funcify.feature.graph.PersistentGraphFactory

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal object DefaultPersistentGraphFactory : PersistentGraphFactory {

    override fun <B : GraphBuilder<B>> builder(): B {
        @Suppress("UNCHECKED_CAST") //
        return DefaultDirectedGraphBuilder() as B
    }
}
