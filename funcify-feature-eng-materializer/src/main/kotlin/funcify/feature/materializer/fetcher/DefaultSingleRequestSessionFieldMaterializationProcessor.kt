package funcify.feature.materializer.fetcher

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.materializer.service.SingleRequestMaterializationDispatchService
import funcify.feature.materializer.service.SingleRequestMaterializationGraphService
import funcify.feature.materializer.service.SingleRequestMaterializationOrchestratorService
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.GraphQLNamedOutputType
import java.util.concurrent.Executor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-03
 */
internal class DefaultSingleRequestSessionFieldMaterializationProcessor(
    private val asyncExecutor: Executor,
    private val singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
    private val singleRequestMaterializationPreprocessingService:
        SingleRequestMaterializationDispatchService,
    private val singleRequestMaterializationOrchestratorService:
        SingleRequestMaterializationOrchestratorService
) : SingleRequestSessionFieldMaterializationProcessor {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestSessionFieldMaterializationProcessor>()
    }

    override fun materializeFieldValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): KFuture<Pair<SingleRequestFieldMaterializationSession, Option<Any>>> {
        val fieldTypeName: String? =
            session.fieldOutputType
                .toOption()
                .filterIsInstance<GraphQLNamedOutputType>()
                .map(GraphQLNamedOutputType::getName)
                .orNull()
        logger.debug(
            """materialize_field_value_in_context: [ 
            |context: { field.name: ${session.field.name}, 
            |field.type: $fieldTypeName }
            |]""".flatten()
        )
        return singleRequestMaterializationGraphService
            .createRequestMaterializationGraphForSession(session)
            .flatMap { s ->
                singleRequestMaterializationPreprocessingService
                    .dispatchRequestsInMaterializationGraphInSession(s)
            }
            .flatMap { s ->
                singleRequestMaterializationOrchestratorService.materializeValueInSession(s)
            }
            .toKFuture()
            .map { l -> l.first() }
    }
}
