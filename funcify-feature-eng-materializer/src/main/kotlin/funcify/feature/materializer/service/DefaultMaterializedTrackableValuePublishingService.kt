package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.tracking.TrackableJsonValuePublisherProvider
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.schema.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLSchema
import java.time.Instant
import java.time.OffsetDateTime
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.Stream.of
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.streams.toList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
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
                        val parentTypeName: String =
                            calculatedValue.targetSourceIndexPath
                                .getParentPath()
                                .flatMap { pp ->
                                    session.metamodelGraph.pathBasedGraph
                                        .getVertex(pp)
                                        .filterIsInstance<SourceContainerTypeVertex>()
                                }
                                .map { sct ->
                                    sct.compositeContainerType.conventionalName.qualifiedForm
                                }
                                .successIfDefined {
                                    MaterializerException(
                                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                                        "could not find parent_type_name for [ path: ${calculatedValue.targetSourceIndexPath} ]"
                                    )
                                }
                                .orElseThrow()
                        val canonicalAndReferenceVertices: Set<SourceAttributeVertex> =
                            session.metamodelGraph
                                .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair
                                .getOrNone(parentTypeName to session.field.name)
                                .fold(::emptySet, ::identity)
                        val canonicalPath: SchematicPath =
                            canonicalAndReferenceVertices
                                .asSequence()
                                .map { sav -> sav.path }
                                .minOrNull()
                                .toOption()
                                .map { sp ->
                                    decoratePathWithRelevantContextualEntityIdentifiers(
                                        sp,
                                        relevantIdsByPath,
                                        calculatedValue,
                                        dispatchedRequest,
                                        session
                                    )
                                }
                                .successIfDefined {
                                    MaterializerException(
                                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                                        "could not determine canonical_path: [ source_index_path: %s ]".format(
                                            calculatedValue.targetSourceIndexPath
                                        )
                                    )
                                }
                                .orElseThrow()
                        val canonicalPathWithoutContext: SchematicPath =
                            canonicalPath.transform { clearArguments() }
                        val referencePaths: PersistentSet<SchematicPath> =
                            canonicalAndReferenceVertices
                                .asSequence()
                                .filter { sav -> sav.path != canonicalPathWithoutContext }
                                .map { sav -> sav.path }
                                .map { refPath ->
                                    decoratePathWithRelevantContextualEntityIdentifiers(
                                        refPath,
                                        relevantIdsByPath,
                                        calculatedValue,
                                        dispatchedRequest,
                                        session
                                    )
                                }
                                .toPersistentSet()
                        logger.info("canonical_path: {}", canonicalPath)
                        logger.info(
                            "reference_paths: {}",
                            referencePaths.asSequence().joinToString(", ", "{ ", " }")
                        )
                        val valueAtTimestamp =
                            relevantTimestampsByPath.values.maxOrNull().toOption().getOrElse {
                                calculatedValue.calculatedTimestamp
                            }
                        logger.info(
                            "value_at_timestamp: [ inputs: {}, result: {} ]",
                            relevantTimestampsByPath.asSequence().joinToString(", ", "{ ", " }"),
                            valueAtTimestamp
                        )
                        calculatedValue.transitionToTracked {
                            canonicalPath(canonicalPath)
                                .referencePaths(referencePaths)
                                .contextualParameters(relevantIdsByPath)
                                .valueAtTimestamp(valueAtTimestamp)
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

    private fun decoratePathWithRelevantContextualEntityIdentifiers(
        canonicalOrReferencePath: SchematicPath,
        relevantIdsByPath: ImmutableMap<SchematicPath, JsonNode>,
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: SingleRequestFieldMaterializationSession,
    ): SchematicPath {
        return when {
            // current relevant contextual parameters have already been determined for the
            // current path
            canonicalOrReferencePath == calculatedValue.targetSourceIndexPath -> {
                canonicalOrReferencePath.transform {
                    clearArguments()
                    arguments(
                        calculatedValue.contextualParameters
                            .asSequence()
                            .filter { (paramPath, _) -> paramPath.arguments.isNotEmpty() }
                            .map { (paramPath, paramVal) ->
                                paramPath.arguments.asIterable().firstOrNone().map { (argName, _) ->
                                    argName to paramVal
                                }
                            }
                            .flatMapOptions()
                            .sortedBy(Pair<String, JsonNode>::first)
                            .reducePairsToPersistentMap()
                    )
                }
            }
            else -> {
                canonicalOrReferencePath.transform {
                    clearArguments()
                    arguments(
                        calculatedValue.contextualParameters
                            .asSequence()
                            .filter { (paramPath, _) -> paramPath.arguments.isNotEmpty() }
                            .map { (p, jn) ->
                                p.arguments.asIterable().firstOrNone().map { (argName, _) ->
                                    argName to jn
                                }
                            }
                            .flatMapOptions()
                            .plus(
                                relevantIdsByPath
                                    .asSequence()
                                    .filter { (sp, _) ->
                                        pathBelongsToSourceAttributeVertexThatAlsoCanServeAsParameterAttributeVertex(
                                            sp,
                                            session
                                        )
                                    }
                                    .map { (sp, jn) ->
                                        sp.pathSegments.lastOrNone().map { lastPathSegment ->
                                            lastPathSegment to jn
                                        }
                                    }
                                    .flatMapOptions()
                            )
                            .distinct()
                            .sortedBy(Pair<String, JsonNode>::first)
                            .reducePairsToPersistentMap()
                    )
                }
            }
        }
    }

    private fun pathBelongsToSourceAttributeVertexThatAlsoCanServeAsParameterAttributeVertex(
        path: SchematicPath,
        session: SingleRequestFieldMaterializationSession,
    ): Boolean {
        return path
            .getParentPath()
            .flatMap { pp ->
                session.metamodelGraph.pathBasedGraph
                    .getVertex(pp)
                    .filterIsInstance<SourceContainerTypeVertex>()
                    .map { sct -> sct.compositeContainerType.conventionalName.qualifiedForm }
                    .zip(
                        session.metamodelGraph.pathBasedGraph
                            .getVertex(path)
                            .filterIsInstance<SourceAttributeVertex>()
                            .map { sa -> sa.compositeAttribute.conventionalName.qualifiedForm }
                    )
            }
            .flatMap { parentTypeChildAttrName ->
                session.metamodelGraph
                    .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair
                    .getOrNone(parentTypeChildAttrName)
            }
            .flatMap { childAttrs ->
                childAttrs
                    .asSequence()
                    .minByOrNull { sav -> sav.path }
                    .toOption()
                    .map { sav -> sav.compositeAttribute.conventionalName.qualifiedForm }
                    .flatMap { name ->
                        session.metamodelGraph.parameterAttributeVerticesByQualifiedName.getOrNone(
                            name
                        )
                    }
            }
            .filter { paramAttrVertices -> paramAttrVertices.isNotEmpty() }
            .isDefined()
    }

    private fun findAnyEntityIdentifierValuesRelatedToThisField(
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: SingleRequestFieldMaterializationSession,
    ): Mono<ImmutableMap<SchematicPath, JsonNode>> {
        return Stream.concat(
                dispatchedRequest.retrievalFunctionSpec.parameterVerticesByPath.keys
                    .parallelStream()
                    .flatMap { sp ->
                        session.singleRequestSession.requestParameterMaterializationGraphPhase
                            .map { phase -> phase.requestGraph.getEdgesTo(sp) }
                            .fold(::empty, ::identity)
                    }
                    .map { edge -> edge.id.first }
                    .flatMap { sourceIndPath ->
                        session.singleRequestSession.requestParameterMaterializationGraphPhase
                            .map { phase -> phase.requestGraph.getEdgesTo(sourceIndPath) }
                            .fold(::empty, ::identity)
                    }
                    .recurse { edge ->
                        when {
                            session.singleRequestSession.requestParameterMaterializationGraphPhase
                                .flatMap { phase ->
                                    phase.materializedParameterValuesByPath[edge.id.first]
                                        .toOption()
                                }
                                .isDefined() -> {
                                session.singleRequestSession
                                    .requestParameterMaterializationGraphPhase
                                    .flatMap { phase ->
                                        phase.retrievalFunctionSpecByTopSourceIndexPath[
                                                edge.id.second]
                                            .toOption()
                                    }
                                    .fold(::empty, ::of)
                                    .map { spec -> edge.id.second.right() }
                            }
                            else -> {
                                session.singleRequestSession
                                    .requestParameterMaterializationGraphPhase
                                    .map { phase -> phase.requestGraph.getEdgesTo(edge.id.first) }
                                    .fold(::empty, ::identity)
                                    .map { parentEdge -> parentEdge.left() }
                            }
                        }
                    }
                    .flatMap { topSrcIndPath ->
                        when {
                            session.metamodelGraph.entityRegistry
                                .pathBelongsToEntitySourceContainerTypeVertex(topSrcIndPath) -> {
                                session.metamodelGraph.entityRegistry
                                    .getEntityIdentifierAttributeVerticesBelongingToSourceContainerIndexPath(
                                        topSrcIndPath
                                    )
                                    .stream()
                            }
                            else -> {
                                session.metamodelGraph.entityRegistry
                                    .findNearestEntityIdentifierPathRelatives(topSrcIndPath)
                                    .stream()
                            }
                        }
                    },
                dispatchedRequest.sourceIndexPath
                    .toOption()
                    .map { p ->
                        session.metamodelGraph.entityRegistry
                            .findNearestEntityIdentifierPathRelatives(p)
                            .stream()
                    }
                    .fold(::empty, ::identity)
            )
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
                        otherTrackableValue.targetSourceIndexPath to
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
