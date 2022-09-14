package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
    private val materializedTrackableValuePublishingService:
        MaterializedTrackableValuePublishingService
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any> {
        logger.info("materialize_value_in_session: [ session.session_id: ${session.sessionId} ]")
        logger.info("field: {}", session.dataFetchingEnvironment.field)
        logger.info("field_result_path: {}", session.dataFetchingEnvironment.executionStepInfo.path)
        val sourceTypeName: String =
            session.dataFetchingEnvironment
                .getSource<Any>()
                .toOption()
                .mapNotNull { s -> s::class.qualifiedName }
                .getOrElse { "<NA>" }
        logger.info(
            "data_fetching_environment.source: [ type: {}, value: {} ]",
            sourceTypeName,
            session.dataFetchingEnvironment.getSource<Any>()
        )
        if (
            !session.singleRequestSession.requestParameterMaterializationGraphPhase.isDefined() ||
                !session.singleRequestSession.requestDispatchMaterializationGraphPhase.isDefined()
        ) {
            logger.error(
                """materialize_value_in_session: 
                |[ status: failed ] 
                |session has not been updated with a 
                |request_materialization_graph or dispatched requests; 
                |a key processing step has been skipped!""".flatten()
            )
            return Mono.error(
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """materialization_processing_step: 
                        |[ request_materialization_graph_creation or request_dispatching ] 
                        |has been skipped""".flatten()
                )
            )
        }
        val currentFieldPathWithoutListIndexing =
            SchematicPath.of {
                pathSegments(session.dataFetchingEnvironment.executionStepInfo.path.keysOnly)
            }
        val currentFieldPath: SchematicPath =
            session.dataFetchingEnvironment.executionStepInfo.path
                .toOption()
                .map { rp -> rp.toString().split("/").asSequence().filter { s -> s.isNotEmpty() } }
                .map { sSeq -> SchematicPath.of { pathSegments(sSeq.toList()) } }
                .getOrElse { currentFieldPathWithoutListIndexing }
        logger.info(
            "current_field_path_without_list_indexing: ${currentFieldPathWithoutListIndexing}"
        )
        logger.info("current_field_path_with_list_indexing: ${currentFieldPath}")
        return when {
            currentFieldPathWithoutListIndexing in
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .orNull()!!
                    .trackableSingleValueRequestDispatchesBySourceIndexPath -> {
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.trackableSingleValueRequestDispatchesBySourceIndexPath
                            .getOrNone(currentFieldPathWithoutListIndexing)
                            .map { sr -> sr.dispatchedTrackableValueRequest }
                    }
                    .successIfDefined { ->
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "unable to map field_path to trackable_single_value_request: [ field_path: ${currentFieldPath} ]"
                        )
                    }
                    .toMono()
                    .flatMap { df ->
                        df.flatMap { trackableJsonValue ->
                            logger.info("trackable_json_value: [ {} ]", trackableJsonValue)
                            // TODO: Add when expression and handle case when value still "planned"
                            trackableJsonValue
                                .toOption()
                                .filterIsInstance<TrackableValue.CalculatedValue<JsonNode>>()
                                .map { cv -> cv.calculatedValue }
                                .orElse {
                                    trackableJsonValue
                                        .toOption()
                                        .filterIsInstance<TrackableValue.TrackedValue<JsonNode>>()
                                        .map { tv -> tv.trackedValue }
                                }
                                .zip(
                                    session.fieldOutputType.toOption().orElse {
                                        session.dataFetchingEnvironment.fieldDefinition.type
                                            .toOption()
                                    }
                                )
                                .flatMap { (jn, gqlOutputType) ->
                                    JsonNodeToStandardValueConverter.invoke(jn, gqlOutputType)
                                }
                                .toMono()
                                .doOnNext { materializedValue ->
                                    materializedTrackableValuePublishingService
                                        .publishMaterializedTrackableJsonValueIfApplicable(
                                            session,
                                            trackableJsonValue,
                                            materializedValue
                                        )
                                }
                        }
                    }
            }
            currentFieldPathWithoutListIndexing in
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .orNull()!!
                    .externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath -> {
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath.getOrNone(
                            currentFieldPathWithoutListIndexing
                                                                                                              )
                    }
                    .map { mr ->
                        mr.dispatchedMultipleIndexRequest.map { deferredResultMap ->
                            deferredResultMap.getOrNone(currentFieldPathWithoutListIndexing)
                        }
                    }
                    .map { df ->
                        df.flatMap { jsonNodeOpt ->
                            jsonNodeOpt
                                .zip(
                                    session.fieldOutputType.toOption().orElse {
                                        session.dataFetchingEnvironment.fieldDefinition.type
                                            .toOption()
                                    }
                                )
                                .flatMap { (jn, gqlOutputType) ->
                                    JsonNodeToStandardValueConverter.invoke(jn, gqlOutputType)
                                }
                                .toMono()
                        }
                    }
                    .successIfDefined { ->
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "unable to map current_field_path to multiple_source_index_request: [ field_path: ${currentFieldPath} ]"
                        )
                    }
                    .toMono()
                    .flatMap { nestedMono -> nestedMono }
            }
            session.dataFetchingEnvironment
                .getSource<Any>()
                .toOption()
                .filterIsInstance<Map<String, JsonNode>>()
                .isDefined() -> {

                session.dataFetchingEnvironment
                    .getSource<Map<String, JsonNode>>()
                    .toOption()
                    .flatMap { m -> m.getOrNone(session.dataFetchingEnvironment.field.name) }
                    .zip(
                        session.dataFetchingEnvironment.fieldType.toOption().orElse {
                            session.dataFetchingEnvironment.fieldDefinition.type.toOption()
                        }
                    )
                    .successIfDefined {
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "unable to map field_path to child entry of json_node map source: [ field_path: ${currentFieldPath} ]"
                        )
                    }
                    .map { (jn, gqlType) -> JsonNodeToStandardValueConverter.invoke(jn, gqlType) }
                    .toMono()
                    .flatMap { resultOpt -> resultOpt.toMono() }
            }
            session.dataFetchingEnvironment
                .getSource<Any>()
                .toOption()
                .filterIsInstance<List<JsonNode>>()
                .isDefined() -> {

                session.dataFetchingEnvironment
                    .getSource<List<JsonNode>>()
                    .toOption()
                    .zip(
                        session.dataFetchingEnvironment.executionStepInfo.path.toOption().map { rp
                            ->
                            if (rp.isListSegment) {
                                rp.segmentIndex
                            } else {
                                0
                            }
                        }
                    )
                    .flatMap { (resultList, index) ->
                        if (index < resultList.size) {
                            resultList[index].toOption()
                        } else {
                            none()
                        }
                    }
                    .zip(
                        session.dataFetchingEnvironment.fieldType.toOption().orElse {
                            session.dataFetchingEnvironment.fieldDefinition.type.toOption()
                        }
                    )
                    .successIfDefined {
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "unable to map field_path to index of json_node list source: [ field_path: ${currentFieldPath} ]"
                        )
                    }
                    .map { (jn, gqlType) -> JsonNodeToStandardValueConverter.invoke(jn, gqlType) }
                    .toMono()
                    .flatMap { resultOpt -> resultOpt.toMono() }
            }
            session.dataFetchingEnvironment
                .getSource<Any>()
                .toOption()
                .filterIsInstance<JsonNode>()
                .isDefined() -> {
                session.dataFetchingEnvironment
                    .getSource<JsonNode>()
                    .toOption()
                    .filterIsInstance<ObjectNode>()
                    .filter { on -> on.has(session.dataFetchingEnvironment.field.name) }
                    .map { on -> on.get(session.dataFetchingEnvironment.field.name) }
                    .orElse { session.dataFetchingEnvironment.getSource<JsonNode>().toOption() }
                    .zip(
                        session.dataFetchingEnvironment.fieldType.toOption().orElse {
                            session.dataFetchingEnvironment.fieldDefinition.type.toOption()
                        }
                    )
                    .successIfDefined { ->
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            "unable to map field_path to json_node source: [ field_path: ${currentFieldPath} ]"
                        )
                    }
                    .map { (jn, gqlType) -> JsonNodeToStandardValueConverter.invoke(jn, gqlType) }
                    .toMono()
                    .flatMap { resultOpt -> resultOpt.toMono() }
            }
            else -> {
                Mono.error(
                    MaterializerException(
                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                        "unable to resolve value for field_path: [ field_path: ${currentFieldPath} ]"
                    )
                )
            }
        }
    }
}
