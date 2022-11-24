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
import funcify.feature.datasource.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.execution.ResultPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()

        private val resultPathToSchematicPathsMemoizer:
            (ResultPath) -> Pair<SchematicPath, SchematicPath> by lazy {
            val cache: ConcurrentMap<ResultPath, Pair<SchematicPath, SchematicPath>> =
                ConcurrentHashMap();
            { rp: ResultPath ->
                cache.computeIfAbsent(
                    rp,
                    resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator()
                )!!
            }
        }

        private fun resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator():
            (ResultPath) -> Pair<SchematicPath, SchematicPath> {
            return { resultPath: ResultPath ->
                SchematicPath.of { pathSegments(resultPath.keysOnly) }
                    .let { pathWithoutListIndexing ->
                        resultPath
                            .toOption()
                            .mapNotNull { rp: ResultPath -> rp.toString() }
                            .map { rpStr: String ->
                                rpStr.split("/").asSequence().filter { s -> s.isNotEmpty() }
                            }
                            .map { sSeq: Sequence<String> ->
                                SchematicPath.of { pathSegments(sSeq.toList()) }
                            }
                            .getOrElse { pathWithoutListIndexing } to pathWithoutListIndexing
                    }
            }
        }

        private inline fun <reified T> currentSourceValueIsInstanceOf(
            session: SingleRequestFieldMaterializationSession
        ): Boolean {
            return session.dataFetchingEnvironment.getSource<Any?>() is T
        }
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any> {
        val (currentFieldPath, currentFieldPathWithoutListIndexing) =
            getFieldSchematicPathWithAndWithoutListIndexing(session)
        logger.info(
            """materialize_value_in_session: [ 
            |session_id: ${session.sessionId}, 
            |field.name: ${session.dataFetchingEnvironment.field.name}, 
            |current_field_path: ${currentFieldPath}, 
            |current_field_path_without_list_indexing: ${currentFieldPathWithoutListIndexing} 
            |]""".flatten()
        )
        if (!sessionHasDefinedMaterializationPhases(session)) {
            return createMaterializationPhasesSkippedErrorPublisher()
        }
        return when {
            pathBelongsToTrackableSingleValueRequestDispatch(
                currentFieldPathWithoutListIndexing,
                session
            ) -> {
                handleTrackableSingleValueRequestDispatch(
                    session,
                    currentFieldPathWithoutListIndexing,
                    currentFieldPath
                )
            }
            pathBelongsToTopLevelSourceIndexOfExternalDataSourceValuesRequestDispatch(
                currentFieldPathWithoutListIndexing,
                session
            ) -> {
                handleTopSourceIndexOfExternalDataSourceValuesRequestDispatch(
                    session,
                    currentFieldPathWithoutListIndexing,
                    currentFieldPath
                )
            }
            currentSourceValueIsInstanceOf<Map<String, JsonNode>>(session) -> {
                handleKeyValueJsonMapSourceValue(session, currentFieldPath)
            }
            currentSourceValueIsInstanceOf<List<JsonNode>>(session) -> {
                handleJsonListSourceValue(session, currentFieldPath)
            }
            currentSourceValueIsInstanceOf<JsonNode>(session) -> {
                handleJsonNodeValue(session, currentFieldPath)
            }
            else -> {
                createUnhandledFieldValueErrorPublisher(currentFieldPath)
            }
        }
    }

    private fun getFieldSchematicPathWithAndWithoutListIndexing(
        session: SingleRequestFieldMaterializationSession
    ): Pair<SchematicPath, SchematicPath> {
        return resultPathToSchematicPathsMemoizer(
            session.dataFetchingEnvironment.executionStepInfo.path
        )
    }

    private fun <T> createMaterializationPhasesSkippedErrorPublisher(): Mono<T> {
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

    private fun sessionHasDefinedMaterializationPhases(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.singleRequestSession.requestParameterMaterializationGraphPhase.isDefined() &&
            session.singleRequestSession.requestDispatchMaterializationGraphPhase.isDefined()
    }

    private fun pathBelongsToTrackableSingleValueRequestDispatch(
        currentFieldPathWithoutListIndexing: SchematicPath,
        session: SingleRequestFieldMaterializationSession,
    ): Boolean {
        return currentFieldPathWithoutListIndexing in
            session.singleRequestSession.requestDispatchMaterializationGraphPhase
                .orNull()!!
                .trackableSingleValueRequestDispatchesBySourceIndexPath
    }

    private fun pathBelongsToTopLevelSourceIndexOfExternalDataSourceValuesRequestDispatch(
        currentFieldPathWithoutListIndexing: SchematicPath,
        session: SingleRequestFieldMaterializationSession,
    ): Boolean {
        return currentFieldPathWithoutListIndexing in
            session.singleRequestSession.requestDispatchMaterializationGraphPhase
                .orNull()!!
                .externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
    }

    private fun handleKeyValueJsonMapSourceValue(
        session: SingleRequestFieldMaterializationSession,
        currentFieldPath: SchematicPath,
    ): Mono<Any> {
        return session.dataFetchingEnvironment
            .getSource<Map<String, JsonNode>>()
            .toOption()
            .flatMap { m -> m.getOrNone(session.dataFetchingEnvironment.field.name) }
            .successIfDefined {
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """could not find entry within source 
                    |map for field [ name: %s, path: %s ]"""
                        .flatten()
                        .format(session.dataFetchingEnvironment.field.name, currentFieldPath)
                )
            }
            .zip(
                session.dataFetchingEnvironment.fieldType
                    .toOption()
                    .orElse { session.dataFetchingEnvironment.fieldDefinition.type.toOption() }
                    .successIfDefined(
                        graphQLOutputTypeUndeterminedExceptionSupplier(currentFieldPath)
                    )
            )
            .toMono()
            .flatMap { (jn, gqlType) -> JsonNodeToStandardValueConverter(jn, gqlType).toMono() }
    }

    private fun handleTopSourceIndexOfExternalDataSourceValuesRequestDispatch(
        session: SingleRequestFieldMaterializationSession,
        currentFieldPathWithoutListIndexing: SchematicPath,
        currentFieldPath: SchematicPath,
    ): Mono<Any> {
        return session.singleRequestSession.requestDispatchMaterializationGraphPhase
            .flatMap { phase ->
                phase.externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
                    .getOrNone(currentFieldPathWithoutListIndexing)
            }
            .map { mr ->
                mr.dispatchedMultipleIndexRequest.map { deferredResultMap ->
                    deferredResultMap.getOrNone(currentFieldPathWithoutListIndexing)
                }
            }
            .successIfDefined { ->
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """unable to map current_field_path to 
                    |external_data_source_values_request: 
                    |[ field_path: ${currentFieldPath} ]""".flatten()
                )
            }
            .zip(
                session.fieldOutputType
                    .toOption()
                    .orElse { session.dataFetchingEnvironment.fieldDefinition.type.toOption() }
                    .successIfDefined(
                        graphQLOutputTypeUndeterminedExceptionSupplier(currentFieldPath)
                    )
            )
            .toMono()
            .flatMap { (df, gqlOutputType) ->
                df.flatMap { jnOpt ->
                    jnOpt
                        .flatMap { jn -> JsonNodeToStandardValueConverter(jn, gqlOutputType) }
                        .toMono()
                }
            }
    }

    private fun handleTrackableSingleValueRequestDispatch(
        session: SingleRequestFieldMaterializationSession,
        currentFieldPathWithoutListIndexing: SchematicPath,
        currentFieldPath: SchematicPath,
    ): Mono<Any> {
        return session.singleRequestSession.requestDispatchMaterializationGraphPhase
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
                    logger.info(
                        "trackable_json_value: [ {} ]",
                        trackableJsonValue
                    ) // TODO: Add when expression and handle case when value still "planned"
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
                                session.dataFetchingEnvironment.fieldDefinition.type.toOption()
                            }
                        )
                        .flatMap { (jn, gqlOutputType) ->
                            JsonNodeToStandardValueConverter(jn, gqlOutputType)
                        }
                        .toMono()
                }
            }
    }

    private fun handleJsonListSourceValue(
        session: SingleRequestFieldMaterializationSession,
        currentFieldPath: SchematicPath,
    ): Mono<Any> {
        return session.dataFetchingEnvironment
            .getSource<List<JsonNode>>()
            .toOption()
            .zip(
                session.dataFetchingEnvironment.executionStepInfo.path.toOption().map { rp ->
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
            .successIfDefined {
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "unable to map field_path to index of json_node list source: [ field_path: ${currentFieldPath} ]"
                )
            }
            .zip(
                session.dataFetchingEnvironment.fieldType
                    .toOption()
                    .orElse { session.dataFetchingEnvironment.fieldDefinition.type.toOption() }
                    .successIfDefined(
                        graphQLOutputTypeUndeterminedExceptionSupplier(currentFieldPath)
                    )
            )
            .toMono()
            .flatMap { (jn, gqlType) -> JsonNodeToStandardValueConverter(jn, gqlType).toMono() }
    }

    private fun graphQLOutputTypeUndeterminedExceptionSupplier(
        currentFieldPath: SchematicPath
    ): () -> MaterializerException {
        return {
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """unable to determine graphql_output_type of 
                |field: [ field_path: %s ]"""
                    .flatten()
                    .format(currentFieldPath)
            )
        }
    }

    private fun handleJsonNodeValue(
        session: SingleRequestFieldMaterializationSession,
        currentFieldPath: SchematicPath,
    ): Mono<Any> {
        return session.dataFetchingEnvironment
            .getSource<JsonNode>()
            .toOption()
            .filterIsInstance<ObjectNode>()
            .filter { on -> on.has(session.dataFetchingEnvironment.field.name) }
            .map { on -> on.get(session.dataFetchingEnvironment.field.name) }
            .orElse { session.dataFetchingEnvironment.getSource<JsonNode>().toOption() }
            .successIfDefined { ->
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "unable to map field_path to json_node source: [ field_path: ${currentFieldPath} ]"
                )
            }
            .zip(
                session.dataFetchingEnvironment.fieldType
                    .toOption()
                    .orElse { session.dataFetchingEnvironment.fieldDefinition.type.toOption() }
                    .successIfDefined(
                        graphQLOutputTypeUndeterminedExceptionSupplier(currentFieldPath)
                    )
            )
            .toMono()
            .flatMap { (jn, gqlType) -> JsonNodeToStandardValueConverter(jn, gqlType).toMono() }
    }

    private fun createUnhandledFieldValueErrorPublisher(
        currentFieldPath: SchematicPath
    ): Mono<Any> {
        return Mono.error(
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                "unable to resolve value for field_path: [ field_path: ${currentFieldPath} ]"
            )
        )
    }
}
