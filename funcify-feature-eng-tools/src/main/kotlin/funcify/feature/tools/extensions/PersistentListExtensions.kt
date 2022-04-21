package funcify.feature.tools.extensions

import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object PersistentListExtensions {

    fun <T> Stream<T>.reduceToPersistentList(): PersistentList<T> {
        return this.reduce(
            persistentListOf<T>(),
            { pl: PersistentList<T>, t: T -> pl.add(t) },
            { _: PersistentList<T>, pl2: PersistentList<T> ->
                // Leaf nodes are the same in sequential stream reduction
                // Parallel streaming will not occur on [Spliterator.ORDERED]
                // and any type providing such a spliterator e.g. Lists
                pl2
            }
        )
    }
}
