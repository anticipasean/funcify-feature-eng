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
            { pl1: PersistentList<T>, pl2: PersistentList<T> ->
                /*
                 * Works in both sequential and parallel cases
                 */
                pl1.addAll(pl2)
            }
        )
    }
}
