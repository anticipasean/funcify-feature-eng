package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.none
import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.GraphQLNamedOutputType
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
        val fieldTypeName: String? =
            context.currentFieldOutputType
                .toOption()
                .filterIsInstance<GraphQLNamedOutputType>()
                .map(GraphQLNamedOutputType::getName)
                .orNull()
        logger.debug(
            """materialize_field_value_in_context: [ 
            |context: { field.name: ${context.currentField.name}, 
            |field.type: $fieldTypeName }
            |]""".flatten()
        )
        return KFuture.completed(none())
    }
}
