package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationOrchestratorService :
    SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<Pair<SingleRequestFieldMaterializationSession, Deferred<Option<Any>>>> {
        logger.info("materialize_value_in_session: [ session.session_id: ${session.sessionId} ]")
        logger.info("field: {}", session.dataFetchingEnvironment.field)
        if (
            !session.requestParameterMaterializationGraphPhase.isDefined() ||
                !session.requestDispatchMaterializationGraphPhase.isDefined()
        ) {
            logger.error(
                """materialize_value_in_session: 
                |[ status: failed ] 
                |session has not been updated with a 
                |request_materialization_graph or dispatched requests; 
                |a key processing step has been skipped!""".flatten()
            )
            return Try.failure(
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """materialization_processing_step: 
                        |[ request_materialization_graph_creation or request_dispatching ] 
                        |has been skipped""".flatten()
                )
            )
        }
        val currentFieldPath =
            SchematicPath.of {
                pathSegments(session.dataFetchingEnvironment.executionStepInfo.path.keysOnly)
            }
        return Try.success(
            session to
                session.requestDispatchMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.multipleSourceIndexRequestDispatchesBySourceIndexPath.getOrNone(
                            currentFieldPath
                        )
                    }
                    .map { mr ->
                        mr.dispatchedMultipleIndexRequest.map { deferredResultMap ->
                            deferredResultMap.getOrNone(currentFieldPath)
                        }
                    }
                    .getOrElse {
                        Deferred.failed(
                            MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                "could not find dispatched_request for source_index_path: ${currentFieldPath}"
                            )
                        )
                    }
        )
    }


}
