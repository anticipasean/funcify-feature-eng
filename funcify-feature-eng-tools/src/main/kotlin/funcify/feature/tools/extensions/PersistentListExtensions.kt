package funcify.feature.tools.extensions

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object PersistentListExtensions {

    fun <T> Stream<T>.reduceToPersistentList(): PersistentList<T> {
        return this.reduce(persistentListOf<T>(),
                           { pl: PersistentList<T>, t: T ->
                               pl.add(t)
                           },
                           { pl1: PersistentList<T>, pl2: PersistentList<T> ->
                               /**
                                * pl1 addAll pl2 preserves insertion order in combiner function
                                */
                               pl1.addAll(pl2)
                           })
    }

}