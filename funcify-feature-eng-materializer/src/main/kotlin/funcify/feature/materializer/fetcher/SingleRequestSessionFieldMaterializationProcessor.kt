package funcify.feature.materializer.fetcher

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface SingleRequestSessionFieldMaterializationProcessor {

    fun materializeFieldValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): KFuture<Pair<SingleRequestFieldMaterializationSession, Option<Any>>>
}
