package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<Pair<SingleRequestFieldMaterializationSession, KFuture<Option<Any>>>> {
        logger.info("materialize_value_in_session: [ session.session_id: ${session.sessionId} ]")
        logger.info("field: {}", session.dataFetchingEnvironment.field)
        logger.info("field_result_path: {}", session.dataFetchingEnvironment.executionStepInfo.path)
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
        val currentFieldPathWithoutIndexing =
            SchematicPath.of {
                pathSegments(session.dataFetchingEnvironment.executionStepInfo.path.keysOnly)
            }
        val currentFieldPath: SchematicPath =
            session.dataFetchingEnvironment.executionStepInfo.path
                .toOption()
                .map { rp -> rp.toString().split("/").asSequence().filter { s -> s.isNotEmpty() } }
                .map { sSeq -> SchematicPath.of { pathSegments(sSeq.toList()) } }
                .getOrElse { currentFieldPathWithoutIndexing }
        logger.info(
            "current_field_path_without_indexing: ${currentFieldPathWithoutIndexing}, \ncurrent_field_path: ${currentFieldPath}"
        )
        return when {
                currentFieldPathWithoutIndexing in
                    session.requestDispatchMaterializationGraphPhase
                        .orNull()!!
                        .multipleSourceIndexRequestDispatchesBySourceIndexPath -> {
                    session.requestDispatchMaterializationGraphPhase
                        .flatMap { phase ->
                            phase.multipleSourceIndexRequestDispatchesBySourceIndexPath.getOrNone(
                                currentFieldPathWithoutIndexing
                            )
                        }
                        .map { mr ->
                            mr.dispatchedMultipleIndexRequest.map { deferredResultMap ->
                                deferredResultMap.getOrNone(currentFieldPathWithoutIndexing)
                            }
                        }
                }
                currentFieldPathWithoutIndexing in
                    session.requestDispatchMaterializationGraphPhase
                        .orNull()!!
                        .cacheableSingleSourceIndexRequestDispatchesBySourceIndexPath -> {
                    session.requestDispatchMaterializationGraphPhase.flatMap { phase ->
                        phase.cacheableSingleSourceIndexRequestDispatchesBySourceIndexPath
                            .getOrNone(currentFieldPathWithoutIndexing)
                            .map { sr -> sr.dispatchedSingleIndexCacheRequest }
                    }
                }
                else -> {
                    session.requestParameterMaterializationGraphPhase.flatMap { graphPhase ->
                        graphPhase.requestGraph
                            .getEdgesTo(currentFieldPathWithoutIndexing)
                            .filter { edge ->
                                edge is RequestParameterEdge.DependentValueRequestParameterEdge
                            }
                            .map { edge ->
                                edge as RequestParameterEdge.DependentValueRequestParameterEdge
                            }
                            .map { edge ->
                                session.requestDispatchMaterializationGraphPhase
                                    .flatMap { dispatchPhase ->
                                        dispatchPhase
                                            .multipleSourceIndexRequestDispatchesBySourceIndexPath[
                                                edge.id.first]
                                            .toOption()
                                    }
                                    .map { mr ->
                                        mr.dispatchedMultipleIndexRequest.map { resultMap ->
                                            logger.info(
                                                "dispatched_multi_index_request.result_map: [ {} ]",
                                                resultMap
                                                    .asSequence()
                                                    .joinToString(
                                                        ",\n",
                                                        "{ ",
                                                        " }",
                                                        transform = { (k, v) -> "$k: $v" }
                                                    )
                                            )
                                            logger.info("edge_for_extraction: [ id: {} ]", edge.id)
                                            resultMap.getOrNone(currentFieldPath)
                                        }
                                    }
                            }
                            .flatMapOptions()
                            .findFirst()
                            .toOption()
                    }
                }
            }
            .successIfDefined({ ->
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "could not find dispatched_request for source_index_path: ${currentFieldPath}"
                )
            })
            .map { df ->
                df.map { jsonNodeOpt -> jsonNodeOpt.flatMap { jn -> jsonNodeToScalarValue(jn) } }
            }
            .map { df -> session to df }
    }

    fun jsonNodeToScalarValue(jsonNode: JsonNode): Option<Any> {
        return when (jsonNode.nodeType) {
            JsonNodeType.MISSING,
            JsonNodeType.NULL -> none()
            JsonNodeType.BOOLEAN,
            JsonNodeType.NUMBER,
            JsonNodeType.BINARY,
            JsonNodeType.STRING -> {
                jsonMapper.fromJsonNode(jsonNode).toKotlinObject(Any::class).getSuccess()
            }
            JsonNodeType.ARRAY -> {
                jsonMapper.fromJsonNode(jsonNode).toKotlinObject(List::class).getSuccess()
            }
            JsonNodeType.OBJECT,
            JsonNodeType.POJO -> {
                jsonMapper.fromJsonNode(jsonNode).toKotlinObject(Map::class).getSuccess()
            }
            else -> none()
        }
    }
}
