package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.datasource.graphql.schema.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.datasource.tracking.TrackedJsonValuePublisherProvider
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLSchema
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
    private val trackedJsonValuePublisherProvider: TrackedJsonValuePublisherProvider
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
                            .tap { dr ->
                                publishTrackableValueIfApplicable(
                                    currentFieldPathWithoutListIndexing,
                                    dr,
                                    session
                                )
                            }
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
                        }
                    }
            }
            currentFieldPathWithoutListIndexing in
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .orNull()!!
                    .multipleSourceIndexRequestDispatchesBySourceIndexPath -> {
                session.singleRequestSession.requestDispatchMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.multipleSourceIndexRequestDispatchesBySourceIndexPath.getOrNone(
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

    private fun publishTrackableValueIfApplicable(
        sourceIndexPath: SchematicPath,
        dispatchedTrackableSingleSourceIndexRetrieval:
            SourceIndexRequestDispatch.DispatchedTrackableSingleSourceIndexRetrieval,
        session: SingleRequestFieldMaterializationSession
    ) {
        trackedJsonValuePublisherProvider
            .getTrackedValuePublisherForDataSource(
                dispatchedTrackableSingleSourceIndexRetrieval.trackableValueJsonRetrievalFunction
                    .cacheForDataSourceKey
            )
            .map { publisher ->
                dispatchedTrackableSingleSourceIndexRetrieval.dispatchedTrackableValueRequest
                    .subscribeOn(Schedulers.boundedElastic())
                    .zipWith(
                        findAnyLastUpdatedFieldValuesRelatedToThisField(
                            sourceIndexPath,
                            dispatchedTrackableSingleSourceIndexRetrieval,
                            session
                        )
                    ) { tv, lastUpdatedFields -> tv to lastUpdatedFields }
                    .flatMap { (tv, relatedLastUpdatedFieldPathValues) ->
                        tv.fold({ none() }, { cv -> cv.calculatedTimestamp.some() }, { none() })
                            .flatMap { currentTimestamp ->
                                sequenceOf(currentTimestamp)
                                    .plus(relatedLastUpdatedFieldPathValues.values)
                                    .minOrNull()
                                    .toOption()
                            }
                            .fold(
                                { Mono.empty() },
                                { earliestInstant ->
                                    tv.fold(
                                        { Mono.empty() },
                                        { cv ->
                                            // only submit for publishing if it's a calculated value
                                            // being transitioned to a tracked_value
                                            Mono.just(
                                                cv.transitionToTracked {
                                                    valueAtTimestamp(earliestInstant)
                                                        .trackedValue(cv.calculatedValue)
                                                }
                                            )
                                        },
                                        { Mono.empty() }
                                    )
                                }
                            )
                    }
                    .subscribe { tv -> publisher.publishTrackedValue(tv) }
            }
    }

    private fun findAnyLastUpdatedFieldValuesRelatedToThisField(
        sourceIndexPath: SchematicPath,
        dispatchedTrackableSingleSourceIndexRetrieval:
            SourceIndexRequestDispatch.DispatchedTrackableSingleSourceIndexRetrieval,
        session: SingleRequestFieldMaterializationSession
    ): Mono<ImmutableMap<SchematicPath, Instant>> {
        return dispatchedTrackableSingleSourceIndexRetrieval
            .backupBaseMultipleSourceIndicesJsonRetrievalFunction
            .parameterPaths
            .asSequence()
            .map { paramPath ->
                session.singleRequestSession.requestParameterMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.requestGraph
                            .getEdgesTo(paramPath)
                            .map { edge -> edge.id.first }
                            .findFirst()
                            .toOption()
                    }
                    .flatMap { dependentSourceIndex ->
                        session.metamodelGraph.lastUpdatedTemporalAttributePathRegistry
                            .findNearestLastUpdatedTemporalAttributePathRelative(
                                dependentSourceIndex
                            )
                    }
            }
            .flatMapOptions()
            .distinct()
            .map { relatedLastUpdatedFieldPath ->
                session.singleRequestSession.requestDispatchMaterializationGraphPhase.flatMap {
                    phase ->
                    phase.multipleSourceIndexRequestDispatchesBySourceIndexPath
                        .asSequence()
                        .filter { (_, retr) ->
                            retr.multipleSourceIndicesJsonRetrievalFunction.sourcePaths.contains(
                                relatedLastUpdatedFieldPath
                            )
                        }
                        .firstOrNull()
                        .toOption()
                        .zip(
                            getVertexPathWithListIndexingIfDescendentOfListNode(
                                    relatedLastUpdatedFieldPath,
                                    session.materializationSchema
                                )
                                .toOption()
                        )
                        .map { (retrEntry, lastUpdatedWithListInd) ->
                            retrEntry.value.dispatchedMultipleIndexRequest
                                .mapNotNull { resultMap -> resultMap[lastUpdatedWithListInd] }
                                .map { jn -> relatedLastUpdatedFieldPath to jn!! }
                        }
                }
            }
            .flatMapOptions()
            .map { lastUpdatedAttrJsonMono ->
                lastUpdatedAttrJsonMono.flatMap { (p, jn) ->
                    // TODO: Add type info fetching and separate temporal type assessment for
                    // applicability to other temporal types
                    jsonMapper
                        .fromJsonNode(jn)
                        .toKotlinObject(OffsetDateTime::class)
                        .map { odt -> p to odt.toInstant() }
                        .toMono()
                }
            }
            .let { lastUpdatedAttrMonoSeq ->
                Flux.merge(lastUpdatedAttrMonoSeq.asIterable()).reduce(
                    persistentMapOf<SchematicPath, Instant>()
                ) { pm, (k, v) -> pm.put(k, v) }
            }
            .widen()
    }

    private fun getVertexPathWithListIndexingIfDescendentOfListNode(
        sourceIndexPath: SchematicPath,
        graphQLSchema: GraphQLSchema
    ): SchematicPath {
        return sourceIndexPath
            .toOption()
            .flatMap { sp ->
                sp.pathSegments.firstOrNone().flatMap { n ->
                    graphQLSchema.queryType.getFieldDefinition(n).toOption().map { gfd ->
                        sp.pathSegments.toPersistentList().removeAt(0) to gfd
                    }
                }
            }
            .fold(::emptySequence, ::sequenceOf)
            .recurse { (ps, gqlf) ->
                when (gqlf.type) {
                    is GraphQLNonNull -> {
                        if ((gqlf.type as GraphQLNonNull).wrappedType is GraphQLList) {
                            sequenceOf(
                                StringBuilder(gqlf.name)
                                    .append('[')
                                    .append(0)
                                    .append(']')
                                    .toString()
                                    .right()
                            )
                        } else {
                            sequenceOf(gqlf.name.right())
                        }
                    }
                    is GraphQLList -> {
                        sequenceOf(
                            StringBuilder(gqlf.name)
                                .append('[')
                                .append(0)
                                .append(']')
                                .toString()
                                .right()
                        )
                    }
                    else -> {
                        sequenceOf(gqlf.name.right())
                    }
                }.plus(
                    GraphQLOutputFieldsContainerTypeExtractor.invoke(gqlf.type)
                        .zip(ps.firstOrNone())
                        .flatMap { (c, n) -> c.getFieldDefinition(n).toOption() }
                        .map { f -> (ps.removeAt(0) to f).left() }
                        .fold(::emptySequence, ::sequenceOf)
                )
            }
            .let { sSeq -> SchematicPath.of { pathSegments(sSeq.toList()) } }
    }
}
