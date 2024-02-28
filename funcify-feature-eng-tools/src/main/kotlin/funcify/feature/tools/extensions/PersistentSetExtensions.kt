package funcify.feature.tools.extensions

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import java.util.stream.Stream

object PersistentSetExtensions {

    fun <T> Stream<out T>.reduceToPersistentSet(): PersistentSet<T> {
        return this.reduce(persistentSetOf(), PersistentSet<T>::add, PersistentSet<T>::addAll)
    }

    fun <T> Stream<out T>.toPersistentSet(): PersistentSet<T> {
        return this.reduceToPersistentSet()
    }

    fun <T> Stream<out T>.reduceToImmutableSet(): ImmutableSet<T> {
        return this.reduceToPersistentSet()
    }

    fun <T> Stream<out T>.toImmutableSet(): PersistentSet<T> {
        return this.reduceToPersistentSet()
    }
}
