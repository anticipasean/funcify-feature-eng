package funcify.feature.materializer.dispatch.context

import arrow.core.Either
import funcify.feature.materializer.input.RawInputContext.CommaSeparatedValues
import funcify.feature.materializer.input.RawInputContext.StandardJson
import funcify.feature.materializer.input.RawInputContext.TabularJson
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-09
 */
interface DispatchInputContext {

    val variables: ImmutableMap<String, Any?>

    interface TabularRawInput : DispatchInputContext {

        val csvOrTabularJson: Either<CommaSeparatedValues, TabularJson>
    }

    interface StandardJsonRawInput : DispatchInputContext {

        val standardJson: StandardJson
    }
}
