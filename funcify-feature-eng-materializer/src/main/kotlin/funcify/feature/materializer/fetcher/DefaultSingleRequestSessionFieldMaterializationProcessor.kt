package funcify.feature.materializer.fetcher

import arrow.core.Option
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-03
 */
internal class DefaultSingleRequestSessionFieldMaterializationProcessor :
    SingleRequestSessionFieldMaterializationProcessor {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestSessionFieldMaterializationProcessor>()
    }

    override fun materializeFieldValueInContext(
        context: SingleRequestFieldMaterializationContext
    ): KFuture<Option<Any>> {
        logger.debug(
            """materialize_field_value_in_context: [ 
            |context: { field.name: ${context.currentField.name}, 
            |field.type: ${context.currentFieldOutputType} }
            |]""".trimMargin()
        )
        TODO("Not yet implemented")
    }
}
