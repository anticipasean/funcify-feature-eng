package funcify.feature.tools.extensions

import arrow.core.Option
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object OptionExtensions {

    fun <T> Option<T>.stream(): Stream<T> {
        return this.fold({ Stream.empty() },
                         { t -> Stream.ofNullable(t) })
    }

    fun <T, O : Option<T>> Stream<O>.flatMapOptions(): Stream<T> {
        return this.flatMap { opt: Option<T> ->
            opt.fold({ Stream.empty() },
                     { t: T -> Stream.ofNullable(t) })
        }
    }

    fun <T> Option<T>.toPersistentSet(): PersistentSet<T> {
        return this.fold({ persistentSetOf() },
                         { t: T -> persistentSetOf(t) })
    }

}