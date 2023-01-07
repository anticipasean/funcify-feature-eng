package funcify.feature.graph

import funcify.feature.graph.builder.DefaultPersistentGraphFactory

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
interface PersistentGraphFactory {

    companion object {

        fun defaultFactory(): PersistentGraphFactory {
            return DefaultPersistentGraphFactory
        }
    }

    fun <B : GraphBuilder<B>> builder(): B
}
