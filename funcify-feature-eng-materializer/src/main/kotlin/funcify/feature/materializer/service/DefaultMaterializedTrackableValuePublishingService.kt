package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableJsonValuePublisherProvider
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.schema.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.GraphQLSchema
import java.time.Instant
import java.time.OffsetDateTime
import java.util.stream.Stream.empty
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.streams.toList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 *
 * @author smccarron
 * @created 2022-09-13
 */
internal class DefaultMaterializedTrackableValuePublishingService(
    private val jsonMapper: JsonMapper,
    private val trackableJsonValuePublisherProvider: TrackableJsonValuePublisherProvider
) : MaterializedTrackableValuePublishingService {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializedTrackableValuePublishingService>()
    }

    override fun publishMaterializedTrackableJsonValueIfApplicable(
        session: SingleRequestFieldMaterializationSession,
        materializedTrackableJsonValue: TrackableValue<JsonNode>,
        materializedValue: Any,
    ) {
        logger.info(
            """publish_materialized_trackable_json_value_if_applicable: [ 
            |trackable_json_value: { type: {}, 
            |source_index_path: {} 
            |} ]""".flatten(),
            materializedTrackableJsonValue::class
                .supertypes
                .asIterable()
                .firstOrNone()
                .mapNotNull(KType::classifier)
                .filterIsInstance<KClass<*>>()
                .map(KClass<*>::simpleName)
                .orNull(),
            materializedTrackableJsonValue.targetSourceIndexPath
        )
        session.singleRequestSession.requestDispatchMaterializationGraphPhase
            .flatMap { phase: RequestDispatchMaterializationPhase ->
                phase.trackableSingleValueRequestDispatchesBySourceIndexPath.getOrNone(
                    materializedTrackableJsonValue.targetSourceIndexPath
                )
            }
            .flatMap {
                trackableSingleJsonValueDispatch:
                    SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch ->
                trackableJsonValuePublisherProvider
                    .getTrackableJsonValuePublisherForDataSource(
                        trackableSingleJsonValueDispatch.trackableJsonValueRetriever
                            .cacheForDataSourceKey
                    )
                    .map { publisher -> trackableSingleJsonValueDispatch to publisher }
            }
            .zip(
                materializedTrackableJsonValue
                    .toOption()
                    .filter { tv: TrackableValue<JsonNode> -> tv.isCalculated() }
                    .filterIsInstance<TrackableValue.CalculatedValue<JsonNode>>()
            ) { (dr, pub), cv -> Triple(dr, pub, cv) }
            .map { (dispatchedRequest, publisher, calculatedValue) ->
                when {
                        dispatchedRequestForCalculatedValueDependentOnOtherTrackableValues(
                            dispatchedRequest,
                            calculatedValue,
                            session
                        ) -> {
                            gatherAnyValueAtTimestampsFromTrackedValuesUsedAsInputForCalculatedValue(
                                    dispatchedRequest,
                                    calculatedValue,
                                    session
                                )
                                .zipWith(
                                    findAnyLastUpdatedFieldValuesRelatedToThisField(
                                        calculatedValue.targetSourceIndexPath,
                                        dispatchedRequest,
                                        session
                                    )
                                ) { m1, m2 -> m1.toPersistentMap().putAll(m2) }
                        }
                        else -> {
                            findAnyLastUpdatedFieldValuesRelatedToThisField(
                                calculatedValue.targetSourceIndexPath,
                                dispatchedRequest,
                                session
                            )
                        }
                    }
                    .zipWith(
                        findAnyEntityIdentifierValuesRelatedToThisField(
                            calculatedValue,
                            dispatchedRequest,
                            session
                        )
                    ) { ts, ids -> ts to ids }
                    .subscribeOn(Schedulers.boundedElastic())
                    .zipWith(
                        jsonMapper.fromKotlinObject(materializedValue).toJsonNode().toMono()
                    ) { (ts, ids), jn -> Triple(ts, ids, jn) }
                    .map { (relevantTimestampsByPath, relevantIdsByPath, materializedJsonValue) ->
                        logger.info(
                            "relevant_ids_by_path: [ {} ]",
                            relevantIdsByPath
                                .asSequence()
                                .joinToString(", ", transform = { (k, v) -> "${k}: $v" })
                        )
                        calculatedValue.transitionToTracked {
                            contextualParameters(relevantIdsByPath)
                                .valueAtTimestamp(
                                    relevantTimestampsByPath.values
                                        .maxOrNull()
                                        .toOption()
                                        .getOrElse { calculatedValue.calculatedTimestamp }
                                )
                                .trackedValue(materializedJsonValue)
                        }
                    }
                    .filter { tv: TrackableValue<JsonNode> -> tv.isTracked() }
                    .subscribe(
                        { trackedValue: TrackableValue<JsonNode> ->
                            publisher.publishTrackableJsonValue(trackedValue)
                        },
                        { t: Throwable ->
                            logger.error(
                                """publish_materialized_trackable_json_value_if_applicable: 
                                |[ status: failed ] 
                                |[ type: {}, message: {} ]""".flatten(),
                                t::class.simpleName,
                                t.message
                            )
                        }
                    )
            }
    }

    private fun findAnyEntityIdentifierValuesRelatedToThisField(
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: SingleRequestFieldMaterializationSession,
    ): Mono<ImmutableMap<SchematicPath, JsonNode>> {
        return dispatchedRequest.retrievalFunctionSpec.parameterVerticesByPath.keys
            .parallelStream()
            .flatMap { sp ->
                session.singleRequestSession.requestParameterMaterializationGraphPhase
                    .map { phase -> phase.requestGraph.getEdgesTo(sp) }
                    .fold(::empty, ::identity)
            }
            .map { edge -> edge.id.first }
            .flatMap { dependentSourceIndPath ->
                session.metamodelGraph.entityRegistry
                    .findNearestEntityIdentifierPathRelatives(dependentSourceIndPath)
                    .stream()
            }
            .distinct()
            .map { entityIdParamPath ->
                session.singleRequestSession.requestParameterMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.materializedParameterValuesByPath.getOrNone(entityIdParamPath)
                    }
                    .map { resultJson -> Mono.just(entityIdParamPath to resultJson) }
                    .orElse {
                        session.singleRequestSession.requestParameterMaterializationGraphPhase
                            .map { phase -> phase.requestGraph.getEdgesTo(entityIdParamPath) }
                            .fold(::empty, ::identity)
                            .map { edge -> edge.id.first }
                            .map { topSrcIndexPath ->
                                session.singleRequestSession
                                    .requestDispatchMaterializationGraphPhase
                                    .zip(
                                        ListIndexedSchematicPathGraphQLSchemaBasedCalculator(
                                            entityIdParamPath,
                                            session.materializationSchema
                                        )
                                    )
                                    .flatMap { (phase, listIndexedEntityIdPath) ->
                                        phase
                                            .externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
                                            .getOrNone(topSrcIndexPath)
                                            .map { dispatch ->
                                                dispatch.dispatchedMultipleIndexRequest
                                                    .map { resultMap ->
                                                        resultMap
                                                            .getOrNone(listIndexedEntityIdPath)
                                                            .map { resultJson ->
                                                                entityIdParamPath to resultJson
                                                            }
                                                    }
                                                    .flatMap { pairOpt -> pairOpt.toMono() }
                                            }
                                    }
                            }
                            .flatMapOptions()
                            .findFirst()
                            .orElseGet { Mono.empty() }
                            .toOption()
                    }
            }
            .flatMapOptions()
            .toList()
            .let { entityIdPathToValueMonos ->
                Flux.merge(entityIdPathToValueMonos)
                    .reduce(persistentMapOf<SchematicPath, JsonNode>()) { pm, (k, v) ->
                        pm.put(k, v)
                    }
                    .widen()
            }
    }

    private fun dispatchedRequestForCalculatedValueDependentOnOtherTrackableValues(
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return dispatchedRequest.backupBaseExternalDataSourceJsonValuesRetriever.parameterPaths
            .asSequence()
            .filter { paramPath ->
                session.singleRequestSession.requestParameterMaterializationGraphPhase
                    .filter { phase ->
                        phase.requestGraph
                            .getEdgesTo(paramPath)
                            .map { edge -> edge.id.first }
                            .anyMatch { sp ->
                                session.singleRequestSession
                                    .requestDispatchMaterializationGraphPhase
                                    .filter { phase ->
                                        phase.trackableSingleValueRequestDispatchesBySourceIndexPath
                                            .containsKey(sp)
                                    }
                                    .isDefined()
                            }
                    }
                    .isDefined()
            }
            .any()
    }

    private fun gatherAnyValueAtTimestampsFromTrackedValuesUsedAsInputForCalculatedValue(
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        session: SingleRequestFieldMaterializationSession
    ): Mono<ImmutableMap<SchematicPath, Instant>> {
        return dispatchedRequest.backupBaseExternalDataSourceJsonValuesRetriever.parameterPaths
            .asSequence()
            .map { paramPath ->
                session.singleRequestSession.requestParameterMaterializationGraphPhase.flatMap {
                    phase ->
                    phase.requestGraph
                        .getEdgesTo(paramPath)
                        .map { edge -> edge.id.first }
                        .findFirst()
                        .toOption()
                }
            }
            .flatMapOptions()
            .map { srcIndPath ->
                session.singleRequestSession.requestDispatchMaterializationGraphPhase.flatMap {
                    phase ->
                    phase.trackableSingleValueRequestDispatchesBySourceIndexPath.getOrNone(
                        srcIndPath
                    )
                }
            }
            .flatMapOptions()
            .map { dependentTrackableValueDispatchedRequest ->
                dependentTrackableValueDispatchedRequest.dispatchedTrackableValueRequest
                    .filter { otherTrackableValue -> otherTrackableValue.isTracked() }
                    .map { otherTrackableValue ->
                        otherTrackableValue.canonicalPath to
                            (otherTrackableValue as TrackableValue.TrackedValue<JsonNode>)
                                .valueAtTimestamp
                    }
            }
            .let { valueAtTimestampByPathSequence ->
                Flux.merge(valueAtTimestampByPathSequence.asIterable())
                    .reduce(persistentMapOf<SchematicPath, Instant>()) { pm, (k, v) ->
                        pm.put(k, v)
                    }
                    .doOnNext { valueAtTimestampsByDependentTrackedValuePath ->
                        logger.info(
                            "value_at_timestamps_by_dependent_tracked_value_path: [ {} ]",
                            valueAtTimestampsByDependentTrackedValuePath
                                .asSequence()
                                .joinToString(", ", "{ ", " }", transform = { (k, v) -> "$k: $v" })
                        )
                    }
                    .widen()
            }
    }

    private fun findAnyLastUpdatedFieldValuesRelatedToThisField(
        sourceIndexPath: SchematicPath,
        trackableSingleJsonValueDispatch:
            SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: SingleRequestFieldMaterializationSession
    ): Mono<ImmutableMap<SchematicPath, Instant>> {
        return trackableSingleJsonValueDispatch.backupBaseExternalDataSourceJsonValuesRetriever
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
                    phase.externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
                        .asSequence()
                        .filter { (_, retr) ->
                            retr.externalDataSourceJsonValuesRetriever.sourcePaths.contains(
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
    ): Option<SchematicPath> {
        return ListIndexedSchematicPathGraphQLSchemaBasedCalculator(sourceIndexPath, graphQLSchema)
    }
}
