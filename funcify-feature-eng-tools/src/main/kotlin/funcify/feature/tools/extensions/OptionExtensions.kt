package funcify.feature.tools.extensions

import arrow.core.Option
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/5/22
 */
object OptionExtensions {

    fun <T> Stream<Option<T>>.flattenOptionsInStream(): Stream<T> {
        return this.flatMap { opt: Option<T> ->
            opt.fold({ Stream.empty() },
                     { t: T -> Stream.ofNullable(t) })
        }
    }


}