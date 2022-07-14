package funcify.feature.materializer.fetcher

import funcify.feature.tools.container.async.KFuture

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface SingleRequestSessionFieldMaterializationProcessor {

    fun materializeFieldValueInContext(
        singleRequestFieldMaterializationContext: SingleRequestFieldMaterializationContext
    ): KFuture<Any?>

}
