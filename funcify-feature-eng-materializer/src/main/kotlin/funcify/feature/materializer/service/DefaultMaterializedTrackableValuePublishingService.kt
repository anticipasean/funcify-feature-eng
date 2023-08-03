package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.materializer.context.publishing.TrackableValuePublishingContext
import funcify.feature.materializer.context.publishing.TrackableValuePublishingContextFactory
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.schema.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.materializer.schema.path.SchematicPathFieldCoordinatesMatcher
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.json.JsonMapper
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import java.time.Instant
import java.time.OffsetDateTime
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.Stream.of
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.switchIfEmpty

/**
 *
 * @author smccarron
 * @created 2022-09-13
 */
internal class DefaultMaterializedTrackableValuePublishingService(
    private val jsonMapper: JsonMapper,
    private val publishingContextFactory: TrackableValuePublishingContextFactory,
    private val trackableJsonValuePublisherProvider: TrackableJsonValuePublisherProvider
) : MaterializedTrackableValuePublishingService {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializedTrackableValuePublishingService>()
    }

    override fun publishMaterializedTrackableJsonValueIfApplicable(
        session: GraphQLSingleRequestSession,
        materializedTrackableJsonValue: TrackableValue<JsonNode>
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
        session.requestDispatchMaterializationGraphPhase
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
                        trackableSingleJsonValueDispatch.featureJsonValueStore
                            .cacheForDataSourceKey
                    )
                    .map { publisher -> trackableSingleJsonValueDispatch to publisher }
            }
            .zip(
                materializedTrackableJsonValue
                    .toOption()
                    .filter { tv: TrackableValue<JsonNode> -> tv.isCalculated() }
                    .filterIsInstance<TrackableValue.CalculatedValue<JsonNode>>()
            ) { (dr, pub), cv ->
                publishingContextFactory
                    .builder()
                    .graphQLSingleRequestSession(session)
                    .trackableJsonValuePublisher(pub)
                    .dispatchedRequest(dr)
                    .calculatedValue(cv)
                    .build()
            }
            .tapNone {
                logger.info(
                    """publish_materialized_trackable_json_value_if_applicable: 
                    |[ status: trackable_json_value not eligible for publication ] 
                    |[ source_index_path: {} ]""".flatten(),
                    materializedTrackableJsonValue.targetSourceIndexPath
                )
            }
            .map { publishingContext: TrackableValuePublishingContext ->
                transitionCalculatedToTrackedAndPublish(publishingContext)
            }
    }

    private fun transitionCalculatedToTrackedAndPublish(
        publishingContext: TrackableValuePublishingContext
    ): Disposable {
        val publisher: FeatureJsonValuePublisher = publishingContext.publisher
        return Mono.defer {
                findAnyLastUpdatedFieldValuesRelatedToThisFieldInContext(publishingContext)
            }
            .publishOn(Schedulers.boundedElastic())
            .flatMap { updatedContext ->
                when {
                    dispatchedRequestForCalculatedValueDependentOnOtherTrackableValuesInContext(
                        updatedContext
                    ) -> {
                        gatherAnyValueAtTimestampsFromTrackedValuesUsedAsInputForCalculatedValueInContext(
                            updatedContext
                        )
                    }
                    else -> {
                        Mono.just(updatedContext)
                    }
                }
            }
            .flatMap { updatedContext ->
                findAnyEntityIdentifierValuesRelatedToThisField(updatedContext)
            }
            .flatMap { updatedContext ->
                convertMaterializedCalculatedValueFromAndBackIntoJson(
                        updatedContext.calculatedValue
                    )
                    .map { jn -> updatedContext to jn }
            }
            .map { (ctx, materializedJsonValue) ->
                val relevantTimestampsByPath = ctx.lastUpdatedInstantsByPath
                val relevantIdsByPath = ctx.entityIdentifierValuesByPath
                val calculatedValue: TrackableValue.CalculatedValue<JsonNode> = ctx.calculatedValue
                val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch =
                    ctx.dispatchedRequest
                val session: GraphQLSingleRequestSession = ctx.session
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
                        .map { sct -> sct.compositeContainerType.conventionalName.qualifiedForm }
                        .successIfDefined {
                            MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                "could not find parent_type_name for [ path: ${calculatedValue.targetSourceIndexPath} ]"
                            )
                        }
                        .orElseThrow()
                val childAttributeName: String =
                    calculatedValue.targetSourceIndexPath
                        .toOption()
                        .flatMap { sp -> session.metamodelGraph.pathBasedGraph.getVertex(sp) }
                        .filterIsInstance<SourceAttributeVertex>()
                        .map { sav -> sav.compositeAttribute.conventionalName.qualifiedForm }
                        .successIfDefined {
                            MaterializerException(
                                MaterializerErrorResponse.UNEXPECTED_ERROR,
                                "could not find attribute_name for [ path: ${calculatedValue.targetSourceIndexPath} ]"
                            )
                        }
                        .orElseThrow()
                val canonicalAndReferenceVertices: Set<SourceAttributeVertex> =
                    session.metamodelGraph
                        .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair
                        .getOrNone(parentTypeName to childAttributeName)
                        .fold(::emptySet, ::identity)
                val canonicalPath: GQLOperationPath =
                    canonicalAndReferenceVertices
                        .asSequence()
                        .map { sav -> sav.path }
                        .minOrNull()
                        .toOption()
                        .map { sp ->
                            decorateCanonicalPathWithRelevantContextualEntityIdentifiers(
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
                val canonicalPathWithoutContext: GQLOperationPath =
                    canonicalPath.transform { clearArguments() }
                val referencePaths: PersistentSet<GQLOperationPath> =
                    canonicalAndReferenceVertices
                        .asSequence()
                        .filter { sav -> sav.path != canonicalPathWithoutContext }
                        .map { sav -> sav.path }
                        .map { refPath ->
                            decorateReferencePathWithRelevantContextualEntityIdentifiers(
                                refPath,
                                canonicalPathWithoutContext,
                                relevantIdsByPath,
                                calculatedValue,
                                dispatchedRequest,
                                session
                            )
                        }
                        .toPersistentSet()
                logger.info("canonical_path_with_context: {}", canonicalPath)
                logger.info(
                    "reference_paths_with_context: {}",
                    referencePaths.asSequence().joinToString(", ", "{ ", " }")
                )
                val valueAtTimestamp: Instant =
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
                    logger.info(
                        """publish_materialized_trackable_json_value_if_applicable: 
                        |[ status: attempting to publish trackable_json_value ] 
                        |[ source_index_path: {} ]""".flatten(),
                        trackedValue.targetSourceIndexPath
                    )
                    publisher.publish(trackedValue)
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

    private fun convertMaterializedCalculatedValueFromAndBackIntoJson(
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>
    ): Mono<JsonNode> {
        logger.info(
            """convert_materialized_calculated_value_from_and_back_into_json: 
            |[ target_source_index_path: {}, 
            |calculated_value: {} ]
            |""".flatten(),
            calculatedValue.targetSourceIndexPath,
            calculatedValue.calculatedValue
        )
        return calculatedValue.calculatedValue
            .toOption()
            .filter { jn -> jn.isNull }
            .toMono()
            .switchIfEmpty {
                JsonNodeToStandardValueConverter(
                        calculatedValue.calculatedValue,
                        calculatedValue.graphQLOutputType
                    )
                    .successIfDefined {
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """unable to convert calculated_value from 
                            |json into expected_graphql_output_type: 
                            |[ calculated_value: %s, graphql_output_type.name: %s ]"""
                                .flatten()
                                .format(
                                    calculatedValue.calculatedValue,
                                    calculatedValue.graphQLOutputType
                                )
                        )
                    }
                    .flatMap { standardValue ->
                        jsonMapper.fromKotlinObject(standardValue).toJsonNode()
                    }
                    .mapFailure { t: Throwable ->
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """unable to convert calculated_value from 
                            |graphql_output_type into json due to: 
                            |[ type: %s, message: %s ]"""
                                .flatten()
                                .format(t::class.qualifiedName, t.message)
                        )
                    }
                    .toMono()
                    .widen()
            }
            .doOnNext { jn ->
                logger.info(
                    """convert_materialized_calculated_value_from_and_back_into_json: 
                    |[ status: success ] 
                    |[ materialized_value: {} ]
                    |""".flatten(),
                    jn
                )
            }
            .doOnError { t ->
                logger.error(
                    """convert_materialized_calculated_value_from_and_back_into_json: 
                    |[ status: failed ] 
                    |[ type: %s, message: %s ]""".flatten(),
                    t::class.simpleName,
                    t.message
                )
            }
    }

    private fun decorateCanonicalPathWithRelevantContextualEntityIdentifiers(
        canonicalPath: GQLOperationPath,
        relevantIdsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: GraphQLSingleRequestSession
    ): GQLOperationPath {
        return when {
            // current relevant contextual parameters have already been determined for the
            // current path
            canonicalPath == calculatedValue.targetSourceIndexPath -> {
                canonicalPath.transform {
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
                val canonicalDomainPath: GQLOperationPath =
                    GQLOperationPath.of {
                        fields(canonicalPath.selection.firstOrNone().toList())
                    }
                // canonical_context = those used to retrieve some ref_path (i.e. the
                // calculated_value.contextual_parameters) + the domain contextual path values
                canonicalPath.transform {
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
                            .plus(
                                relevantIdsByPath
                                    .asSequence()
                                    .filter { (sp, _) -> canonicalDomainPath.isAncestorOf(sp) }
                                    .map { (sp, jn) ->
                                        sp.selection.lastOrNone().map { ps -> ps to jn }
                                    }
                                    .flatMapOptions()
                            )
                            .sortedBy(Pair<String, JsonNode>::first)
                            .reducePairsToPersistentMap()
                    )
                }
            }
        }
    }

    private fun decorateReferencePathWithRelevantContextualEntityIdentifiers(
        referencePath: GQLOperationPath,
        canonicalPathWithoutContext: GQLOperationPath,
        relevantIdsByPath: ImmutableMap<GQLOperationPath, JsonNode>,
        calculatedValue: TrackableValue.CalculatedValue<JsonNode>,
        dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch,
        session: GraphQLSingleRequestSession,
    ): GQLOperationPath {
        return when {
            // current relevant contextual parameters have already been determined for the
            // current path
            referencePath == calculatedValue.targetSourceIndexPath -> {
                referencePath.transform {
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
                val canonicalDomainPath: GQLOperationPath =
                    GQLOperationPath.of {
                        fields(
                            canonicalPathWithoutContext.selection.firstOrNone().toList()
                              )
                    }
                // reference_context = those used to retrieve the canonical_path (i.e. the
                // calculated_value.contextual_parameters) - the domain contextual path values
                referencePath.transform {
                    clearArguments()
                    arguments(
                        calculatedValue.contextualParameters
                            .asSequence()
                            .filter { (paramPath, _) ->
                                !canonicalDomainPath.isAncestorOf(paramPath)
                            }
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
        }
    }

    private fun findAnyEntityIdentifierValuesRelatedToThisField(
        publishingContext: TrackableValuePublishingContext
    ): Mono<TrackableValuePublishingContext> {
        val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch =
            publishingContext.dispatchedRequest
        val session: GraphQLSingleRequestSession = publishingContext.session
        return Stream.concat(
                dispatchedRequest.retrievalFunctionSpec.parameterVerticesByPath.keys
                    .parallelStream()
                    .flatMap { sp ->
                        session.requestParameterMaterializationGraphPhase
                            .map { phase -> phase.requestGraph.getEdgesFrom(sp) }
                            .fold(::empty, ::identity)
                    }
                    .map { edge -> edge.id.second }
                    .flatMap { sourceIndPath ->
                        session.requestParameterMaterializationGraphPhase
                            .map { phase -> phase.requestGraph.getEdgesFrom(sourceIndPath) }
                            .fold(::empty, ::identity)
                    }
                    .recurse { edge ->
                        when {
                            session.requestParameterMaterializationGraphPhase
                                .flatMap { phase ->
                                    phase.materializedParameterValuesByPath.getOrNone(
                                        edge.id.second
                                    )
                                }
                                .isDefined() -> {
                                session.requestParameterMaterializationGraphPhase
                                    .flatMap { phase ->
                                        phase.retrievalFunctionSpecByTopSourceIndexPath.getOrNone(
                                            edge.id.first
                                        )
                                    }
                                    .fold(::empty, ::of)
                                    .map { spec -> edge.id.first.right() }
                            }
                            else -> {
                                session.requestParameterMaterializationGraphPhase
                                    .map { phase ->
                                        phase.requestGraph.getEdgesFrom(edge.id.second)
                                    }
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
            .map { entityIdSourceIndexPath ->
                session.requestParameterMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.materializedParameterValuesByPath.getOrNone(entityIdSourceIndexPath)
                    }
                    .map { resultJson -> Mono.just(entityIdSourceIndexPath to resultJson) }
                    .orElse {
                        session.requestParameterMaterializationGraphPhase
                            .map { phase ->
                                phase.requestGraph.getEdgesFrom(entityIdSourceIndexPath)
                            }
                            .fold(::empty, ::identity)
                            .map { edge -> edge.id.second }
                            .map { topSrcIndexPath ->
                                session.requestDispatchMaterializationGraphPhase
                                    .zip(
                                        ListIndexedSchematicPathGraphQLSchemaBasedCalculator(
                                            entityIdSourceIndexPath,
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
                                                                entityIdSourceIndexPath to
                                                                    resultJson
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
            .map { entityIdPathToValuePublisher ->
                // Fetch and replace with canonical path for entity identifier
                entityIdPathToValuePublisher.map { (path, jv) ->
                    SchematicPathFieldCoordinatesMatcher(session.materializationMetamodel, path)
                        .flatMap { fc: FieldCoordinates ->
                            session.metamodelGraph
                                .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair
                                .getOrNone(fc.typeName to fc.fieldName)
                        }
                        .fold(::emptySet, ::identity)
                        .asSequence()
                        .filter { sav -> sav.path < path }
                        .map { sav -> sav.path }
                        .firstOrNull()
                        .toOption()
                        .getOrElse { path } to jv
                }
            }
            .toList()
            .let { entityIdPathToValueMonos ->
                Flux.merge(entityIdPathToValueMonos)
                    .reduce(publishingContext) {
                        ctx: TrackableValuePublishingContext,
                        (k: GQLOperationPath, v: JsonNode) ->
                        ctx.update { putEntityIdentifierValueForPath(k, v) }
                    }
                    .widen()
            }
    }

    private fun dispatchedRequestForCalculatedValueDependentOnOtherTrackableValuesInContext(
        publishingContext: TrackableValuePublishingContext
    ): Boolean {
        val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch =
            publishingContext.dispatchedRequest
        val session: GraphQLSingleRequestSession = publishingContext.session
        return dispatchedRequest.backupBaseDataElementJsonValueSource.parameterPaths
            .asSequence()
            .filter { paramPath ->
                session.requestParameterMaterializationGraphPhase
                    .filter { phase ->
                        phase.requestGraph
                            .getEdgesFrom(paramPath)
                            .map { edge -> edge.id.second }
                            .anyMatch { sp ->
                                session.requestDispatchMaterializationGraphPhase
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

    private fun gatherAnyValueAtTimestampsFromTrackedValuesUsedAsInputForCalculatedValueInContext(
        publishingContext: TrackableValuePublishingContext
    ): Mono<TrackableValuePublishingContext> {
        val dispatchedRequest: SourceIndexRequestDispatch.TrackableSingleJsonValueDispatch =
            publishingContext.dispatchedRequest
        val session: GraphQLSingleRequestSession = publishingContext.session
        return dispatchedRequest.retrievalFunctionSpec.parameterVerticesByPath.keys
            .parallelStream()
            .flatMap { paramPath ->
                session.requestParameterMaterializationGraphPhase
                    .map { phase -> phase.requestGraph.getEdgesFrom(paramPath) }
                    .fold(::empty, ::identity)
            }
            .map { edge -> edge.id.second }
            .flatMap { topSrcIndPath ->
                session.requestDispatchMaterializationGraphPhase
                    .flatMap { phase ->
                        phase.trackableSingleValueRequestDispatchesBySourceIndexPath.getOrNone(
                            topSrcIndPath
                        )
                    }
                    .fold(::empty, ::of)
            }
            .map { dependentTrackableValueDispatchedRequest ->
                dependentTrackableValueDispatchedRequest.dispatchedTrackableValueRequest
                    .filter { otherTrackableValue -> otherTrackableValue.isTracked() }
                    .map { otherTrackableValue ->
                        otherTrackableValue.targetSourceIndexPath to
                            (otherTrackableValue as TrackableValue.TrackedValue<JsonNode>)
                                .valueAtTimestamp
                    }
            }
            .let { valueAtTimestampByPathStream ->
                Flux.fromStream(valueAtTimestampByPathStream)
                    .collectList()
                    .flatMapMany { valueAtTimestampEntryPublishers ->
                        Flux.merge(valueAtTimestampEntryPublishers)
                    }
                    .reduce(persistentMapOf<GQLOperationPath, Instant>()) { pm, (k, v) ->
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
                    .map { valueAtTimestampsByDependentTrackedValuePath ->
                        publishingContext.update {
                            putAllLastUpdatedInstantsForPaths(
                                valueAtTimestampsByDependentTrackedValuePath
                            )
                        }
                    }
                    .widen()
            }
    }

    private fun findAnyLastUpdatedFieldValuesRelatedToThisFieldInContext(
        publishingContext: TrackableValuePublishingContext
    ): Mono<TrackableValuePublishingContext> {
        val session: GraphQLSingleRequestSession = publishingContext.session
        return publishingContext.dispatchedRequest.retrievalFunctionSpec.parameterVerticesByPath
            .keys
            .parallelStream()
            .flatMap { sp ->
                session.requestParameterMaterializationGraphPhase
                    .map { phase -> phase.requestGraph.getEdgesFrom(sp) }
                    .fold(::empty, ::identity)
            }
            .map { edge -> edge.id.second }
            .map { sourceIndexForParamPath ->
                session.metamodelGraph.lastUpdatedTemporalAttributePathRegistry
                    .findNearestLastUpdatedTemporalAttributePathRelative(sourceIndexForParamPath)
            }
            .flatMapOptions()
            .distinct()
            .map { lastUpdatedPath ->
                session.requestDispatchMaterializationGraphPhase.flatMap { phase ->
                    phase.externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
                        .asIterable()
                        .firstOrNone { (_, dispatch) ->
                            dispatch.retrievalFunctionSpec.sourceVerticesByPath.containsKey(
                                lastUpdatedPath
                            )
                        }
                        .map { (_, dispatch) ->
                            dispatch.dispatchedMultipleIndexRequest
                                .map { resultMap ->
                                    getVertexPathWithListIndexingIfDescendentOfListNode(
                                            lastUpdatedPath,
                                            session.materializationSchema
                                        )
                                        .flatMap { listIndexedLastUpdatedPath ->
                                            resultMap.getOrNone(listIndexedLastUpdatedPath).map { jn
                                                ->
                                                listIndexedLastUpdatedPath to jn
                                            }
                                        }
                                }
                                .flatMap { pairOpt -> pairOpt.toMono() }
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
                Flux.fromStream(lastUpdatedAttrMonoSeq)
                    .collectList()
                    .flatMapMany { lastUpdatedAttrMonos ->
                        Flux.merge(lastUpdatedAttrMonos).cache()
                    }
                    .reduce(publishingContext) {
                        ctx: TrackableValuePublishingContext,
                        (p: GQLOperationPath, i: Instant) ->
                        ctx.update { putLastUpdatedInstantForPath(p, i) }
                    }
            }
            .widen()
    }

    private fun getVertexPathWithListIndexingIfDescendentOfListNode(
        sourceIndexPath: GQLOperationPath,
        graphQLSchema: GraphQLSchema
    ): Option<GQLOperationPath> {
        return ListIndexedSchematicPathGraphQLSchemaBasedCalculator(sourceIndexPath, graphQLSchema)
    }
}
