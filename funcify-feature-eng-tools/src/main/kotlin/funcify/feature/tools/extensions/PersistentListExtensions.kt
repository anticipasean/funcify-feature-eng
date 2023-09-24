package funcify.feature.tools.extensions

import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * @author smccarron
 * @created 4/5/22
 */
object PersistentListExtensions {

    fun <T> Stream<out T>.reduceToPersistentList(): PersistentList<T> {
        return this.reduce(persistentListOf<T>(), PersistentList<T>::add, PersistentList<T>::addAll)
    }

    fun <T> Stream<out T>.toPersistentList(): PersistentList<T> {
        return this.reduceToPersistentList()
    }

    fun <T> Stream<out T>.toImmutableList(): ImmutableList<T> {
        return this.reduceToPersistentList()
    }

    fun <T> Stream<out T>.reduceToImmutableList(): ImmutableList<T> {
        return this.reduceToPersistentList()
    }
}
