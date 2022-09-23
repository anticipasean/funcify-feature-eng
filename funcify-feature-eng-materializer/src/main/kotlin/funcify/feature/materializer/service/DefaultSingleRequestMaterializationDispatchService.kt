package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.datasource.retrieval.BackupExternalDataSourceCalculatedJsonValueRetriever
import funcify.feature.datasource.retrieval.ExternalDataSourceJsonValuesRetriever
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.retrieval.TrackableJsonValueRetriever
import funcify.feature.datasource.tracking.TrackableValue
import funcify.feature.datasource.tracking.TrackableValueFactory
import funcify.feature.materializer.dispatch.DefaultSourceIndexRequestDispatchFactory
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatch.*
import funcify.feature.materializer.dispatch.SourceIndexRequestDispatchFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.phase.DefaultRequestDispatchMaterializationPhase
import funcify.feature.materializer.phase.RequestDispatchMaterializationPhase
import funcify.feature.materializer.phase.RequestParameterMaterializationGraphPhase
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.schema.edge.RequestParameterEdge.*
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamPairs
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor
import java.util.stream.Stream
import java.util.stream.Stream.empty
import kotlin.streams.asSequence
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

internal class DefaultSingleRequestMaterializationDispatchService(
    private val asyncExecutor: Executor,
    private val schematicPathBasedJsonRetrievalFunctionFactory:
        SchematicPathBasedJsonRetrievalFunctionFactory,
    private val trackableValueFactory: TrackableValueFactory,
    private val sourceIndexRequestDispatchFactory: SourceIndexRequestDispatchFactory =
        DefaultSourceIndexRequestDispatchFactory(),
) : SingleRequestMaterializationDispatchService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationDispatchService>()
        private const val DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS: Int = 4
        private val DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION: Duration =
            Duration.ofSeconds(DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS.toLong())
        private data class RequestCreationContext(
            val processedRetrievalFunctionSpecsBySourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf(),
            val remainingRetrievalFunctionSpecsBySourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf(),
            val multiSrcIndexFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, ExternalDataSourceJsonValuesRetriever> =
                persistentMapOf(),
            val singleSrcIndexCacheFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, TrackableJsonValueRetriever> =
                persistentMapOf(),
            val singleSrcIndexBackupFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, BackupExternalDataSourceCalculatedJsonValueRetriever> =
                persistentMapOf(),
            val dispatchedMultiValueResponsesBySourceIndexPath:
                PersistentMap<SchematicPath, Mono<ImmutableMap<SchematicPath, JsonNode>>> =
                persistentMapOf(),
            val dispatchedTrackableValueResponsesBySourceIndexPath:
                PersistentMap<SchematicPath, Mono<TrackableValue<JsonNode>>> =
                persistentMapOf()
        )
    }

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Try<GraphQLSingleRequestSession> {
        logger.info(
            "dispatch_requests_in_materialization_graph_in_session: [ session.session_id: ${session.sessionId} ]"
        )
        if (!session.requestParameterMaterializationGraphPhase.isDefined()) {
            logger.error(
                """dispatch_requests_in_materialization_graph_in_session: 
                |[ status: failed ] 
                |session has not been updated with a 
                |request_materialization_graph; 
                |a key processing step has been skipped!""".flatten()
            )
            return Try.failure(
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """materialization_processing_step: 
                        |[ request_materialization_graph_creation ] 
                        |has been skipped""".flatten()
                )
            )
        }
        if (session.requestDispatchMaterializationGraphPhase.isDefined()) {
            return Try.success(session)
        }
        session.requestParameterMaterializationGraphPhase.map { phase ->
            logger.info("request_graph: \n{}", createGraphStr(phase.requestGraph))
            logger.info(
                "materialized_parameter_values_by_path: {}",
                phase.materializedParameterValuesByPath
                    .asSequence()
                    .joinToString(",\n", "{ ", " }", transform = { (k, v) -> "$k: $v" })
            )
            logger.info(
                "parameter_index_paths_by_source_index_path: {}",
                phase.parameterIndexPathsBySourceIndexPath
                    .asSequence()
                    .map { (p, ps) ->
                        "${p}: %s".format(ps.asSequence().joinToString(", ", "{ ", " }"))
                    }
                    .joinToString(",\n", "{ ", " }")
            )
            logger.info(
                "retrieval_function_spec_by_top_source_index_path: {}",
                phase.retrievalFunctionSpecByTopSourceIndexPath
                    .asSequence()
                    .map { (srcIndPath, spec) ->
                        "path: ${srcIndPath}: %s, %s".format(
                            spec.parameterVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "parameters: {", " }"),
                            spec.sourceVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "sources: { ", " }")
                        )
                    }
                    .joinToString(",\n", "{ ", " }")
            )
        }
        val requestParameterMaterializationGraphPhase =
            session.requestParameterMaterializationGraphPhase.orNull()!!
        return requestParameterMaterializationGraphPhase.retrievalFunctionSpecByTopSourceIndexPath
            .asSequence()
            .fold(Try.success(RequestCreationContext())) {
                reqCreationContextUpdateAttempt,
                (sourceIndexPath, retrievalFunctionSpec) ->
                reqCreationContextUpdateAttempt.flatMap { context ->
                    createAndDispatchFirstRoundOfRequestFunctionsForApplicableRetrievalFunctionSpecs(
                        session,
                        requestParameterMaterializationGraphPhase,
                        context,
                        sourceIndexPath,
                        retrievalFunctionSpec
                    )
                }
            }
            .flatMap { reqCreationContext ->
                checkWhetherRemainingCanBeResolved(
                    reqCreationContext,
                    session,
                    requestParameterMaterializationGraphPhase
                )
            }
            .flatMap { reqCreationContext ->
                convertToFailureIfAnyRemainingStillPresent(reqCreationContext)
            }
            .flatMap { reqCreationContext ->
                wireBackupFunctionsToCachedSingleValueRequestDispatches(
                    requestParameterMaterializationGraphPhase,
                    reqCreationContext
                )
            }
            .map { reqCreationContext ->
                convertRequestCreationContextIntoRequestDispatchMaterializationPhase(
                    reqCreationContext
                )
            }
            .map { requestDispatchPhase ->
                session.update { requestDispatchMaterializationPhase(requestDispatchPhase) }
            }
            .peekIfSuccess { updatedSession ->
                logger.info(
                    "request_dispatch_phase: {}",
                    createRequestDispatchPhaseString(
                        updatedSession.requestDispatchMaterializationGraphPhase.orNull()!!
                    )
                )
            }
            .peekIfFailure { thr ->
                logger.error(
                    "unable to create request_dispatch_phase: [ type: ${thr::class.qualifiedName}, message: ${thr.message} ]"
                )
            }
    }

    private fun createRequestDispatchPhaseString(
        requestDispatchMaterializationPhase: RequestDispatchMaterializationPhase
    ): String {
        return requestDispatchMaterializationPhase
            .externalDataSourceJsonValuesRequestDispatchesByAncestorSourceIndexPath
            .asSequence()
            .map { (p, m) ->
                "path: $p, multiple_src_ind_request_dispatch: { source_indices: %s, param_indices: %s ]".format(
                    m.externalDataSourceJsonValuesRetriever.sourcePaths
                        .asSequence()
                        .joinToString(", ", "{ ", " }"),
                    m.externalDataSourceJsonValuesRetriever.parameterPaths.joinToString(
                        ", ",
                        "{ ",
                        " }"
                    )
                )
            }
            .joinToString(",\n", "multi_src_ind_request_dispatches: { \n", "\n}\n") +
            "\n" +
            requestDispatchMaterializationPhase
                .trackableSingleValueRequestDispatchesBySourceIndexPath
                .asSequence()
                .map { (p, s) ->
                    "path: $p, single_src_ind_request_dispatch: { source_indices: %s, param_indices: %s ]".format(
                        s.backupBaseExternalDataSourceJsonValuesRetriever.sourcePaths
                            .asSequence()
                            .joinToString(", ", "{ ", " }"),
                        s.backupBaseExternalDataSourceJsonValuesRetriever.parameterPaths
                            .asSequence()
                            .joinToString(", ", "{ ", " }")
                    )
                }
                .joinToString(",\n", "single_src_ind_request_dispatches: {\n", "\n}\n")
    }

    private fun createAndDispatchFirstRoundOfRequestFunctionsForApplicableRetrievalFunctionSpecs(
        session: GraphQLSingleRequestSession,
        graphPhase: RequestParameterMaterializationGraphPhase,
        requestCreationContext: RequestCreationContext,
        sourceIndexPath: SchematicPath,
        retrievalFunctionSpec: RetrievalFunctionSpec,
    ): Try<RequestCreationContext> {
        logger.debug(
            "create_and_dispatch_first_round_of_request_functions_for_applicable_retrieval_function_specs: [ source_index_path: ${sourceIndexPath} ]"
        )
        return when {
            // case 1: all parameters expected for this function_spec have materialized values (=>
            // input values supplied by caller) and a function can be built for this datasource
            retrievalFunctionSpec.parameterVerticesByPath.all { (p, _) ->
                p in graphPhase.materializedParameterValuesByPath
            } &&
                schematicPathBasedJsonRetrievalFunctionFactory
                    .canBuildExternalDataSourceJsonValuesRetrieverForDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                createExternalDataSourceJsonValuesRetrieverForRetrievalFunctionSpec(
                        retrievalFunctionSpec
                    )
                    .map { multiSrcIndJsonRetrFunc ->
                        requestCreationContext.copy(
                            processedRetrievalFunctionSpecsBySourceIndexPath =
                                requestCreationContext
                                    .processedRetrievalFunctionSpecsBySourceIndexPath
                                    .put(sourceIndexPath, retrievalFunctionSpec),
                            multiSrcIndexFunctionBySourceIndexPath =
                                requestCreationContext.multiSrcIndexFunctionBySourceIndexPath.put(
                                    sourceIndexPath,
                                    multiSrcIndJsonRetrFunc
                                ),
                            dispatchedMultiValueResponsesBySourceIndexPath =
                                requestCreationContext
                                    .dispatchedMultiValueResponsesBySourceIndexPath
                                    .put(
                                        sourceIndexPath,
                                        multiSrcIndJsonRetrFunc
                                            .invoke(
                                                retrievalFunctionSpec.parameterVerticesByPath.keys
                                                    .asSequence()
                                                    .map { p ->
                                                        graphPhase
                                                            .materializedParameterValuesByPath[p]
                                                            .toOption()
                                                            .map { jn -> p to jn }
                                                    }
                                                    .flatMapOptions()
                                                    .reducePairsToPersistentMap()
                                            )
                                            .cache()
                                            .timeout(DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION)
                                    )
                        )
                    }
            }
            // case 2: source_index_path represents a single scalar or list (non-object) value that
            // may be tracked (cached, require special consideration or calculation, etc.)
            retrievalFunctionSpec.sourceVerticesByPath
                .asIterable()
                .singleOrNone()
                .filter { (_, srcJunctionOrLeafVertex) -> srcJunctionOrLeafVertex.isRight() }
                .isDefined() &&
                schematicPathBasedJsonRetrievalFunctionFactory
                    .canBuildTrackableJsonValueRetrieverOnBehalfOfDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                schematicPathBasedJsonRetrievalFunctionFactory
                    .trackableValueJsonRetrievalFunctionBuilder()
                    .cacheForDataSource(retrievalFunctionSpec.dataSource)
                    .build()
                    .zip(
                        createExternalDataSourceJsonValuesRetrieverForRetrievalFunctionSpec(
                            retrievalFunctionSpec
                        )
                    )
                    .map {
                        (
                            singleSrcIndCacheRetrFunc: TrackableJsonValueRetriever,
                            multiSrcIndJsonRetrFunc: ExternalDataSourceJsonValuesRetriever
                        ) ->
                        val backupFunction: BackupExternalDataSourceCalculatedJsonValueRetriever =
                            createBackupSingleSourceIndexJsonOptionRetrievalFunctionFor(
                                sourceIndexPath,
                                retrievalFunctionSpec,
                                multiSrcIndJsonRetrFunc,
                                graphPhase.materializedParameterValuesByPath,
                                session
                            )
                        requestCreationContext.copy(
                            processedRetrievalFunctionSpecsBySourceIndexPath =
                                requestCreationContext
                                    .processedRetrievalFunctionSpecsBySourceIndexPath
                                    .put(sourceIndexPath, retrievalFunctionSpec),
                            singleSrcIndexCacheFunctionBySourceIndexPath =
                                requestCreationContext.singleSrcIndexCacheFunctionBySourceIndexPath
                                    .put(sourceIndexPath, singleSrcIndCacheRetrFunc),
                            multiSrcIndexFunctionBySourceIndexPath =
                                requestCreationContext.multiSrcIndexFunctionBySourceIndexPath.put(
                                    sourceIndexPath,
                                    multiSrcIndJsonRetrFunc
                                ),
                            singleSrcIndexBackupFunctionBySourceIndexPath =
                                requestCreationContext.singleSrcIndexBackupFunctionBySourceIndexPath
                                    .put(sourceIndexPath, backupFunction),
                            dispatchedTrackableValueResponsesBySourceIndexPath =
                                requestCreationContext
                                    .dispatchedTrackableValueResponsesBySourceIndexPath
                                    .put(
                                        sourceIndexPath,
                                        createPlannedValueForSourceIndexPath(
                                                sourceIndexPath,
                                                retrievalFunctionSpec,
                                                graphPhase,
                                                session
                                            )
                                            .flatMap { plannedValue ->
                                                singleSrcIndCacheRetrFunc
                                                    .invoke(plannedValue)
                                                    .cache()
                                                    .timeout(DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION)
                                            }
                                    )
                        )
                    }
            }
            // case 3: retrieval_function_spec cannot be processed before calls of these other
            // functions have been made
            else -> {
                Try.success(
                    requestCreationContext.copy(
                        remainingRetrievalFunctionSpecsBySourceIndexPath =
                            requestCreationContext.remainingRetrievalFunctionSpecsBySourceIndexPath
                                .put(sourceIndexPath, retrievalFunctionSpec)
                    )
                )
            }
        }
    }

    private fun createPlannedValueForSourceIndexPath(
        sourceIndexPath: SchematicPath,
        retrievalFunctionSpec: RetrievalFunctionSpec,
        graphPhase: RequestParameterMaterializationGraphPhase,
        session: GraphQLSingleRequestSession
    ): Mono<TrackableValue.PlannedValue<JsonNode>> {
        return retrievalFunctionSpec.sourceVerticesByPath[sourceIndexPath]
            .successIfNonNull {
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """target_source_index_path not present 
                       |within expected retrieval_function_spec 
                       |output source indices 
                       |[ target_source_index_path: %s ]"""
                        .format(sourceIndexPath)
                        .flatten()
                )
            }
            .flatMap { sjvOrSlv ->
                sourceIndexPath
                    .getParentPath()
                    .flatMap { pp -> session.metamodelGraph.pathBasedGraph.getVertex(pp) }
                    .filterIsInstance<SourceContainerTypeVertex>()
                    .map { sct -> sct.compositeContainerType.conventionalName.qualifiedForm }
                    .zip(
                        sjvOrSlv
                            .fold(
                                SourceJunctionVertex::compositeAttribute,
                                SourceLeafVertex::compositeAttribute
                            )
                            .conventionalName
                            .qualifiedForm
                            .some()
                    )
                    .successIfDefined {
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """could not find parent_type_name 
                               |and child_attribute_name for path: 
                               |[ source_index_path: ${sourceIndexPath} ]""".trimMargin()
                        )
                    }
            }
            .toMono()
            .flatMap { (parentTypeName, childAttributeName) ->
                FieldCoordinates.coordinates(parentTypeName, childAttributeName)
                    .toOption()
                    .flatMap { fc: FieldCoordinates ->
                        session.materializationSchema.getFieldDefinition(fc).toOption()
                    }
                    .map(GraphQLFieldDefinition::getType)
                    .successIfDefined {
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """could not find graphql_output_type for field_coordinates: 
                               |[ parent_type_name: %s, 
                               |child_attribute_name: %s 
                               |]"""
                                .flatten()
                                .format(parentTypeName, childAttributeName)
                        )
                    }
                    .toMono()
                    .flatMap { gqlt ->
                        filterMaterializedParameterValuesByPathRelatedToTargetSourceIndexPath(
                                sourceIndexPath,
                                retrievalFunctionSpec,
                                graphPhase,
                                session
                            )
                            .flatMap { filteredContextParameterValues ->
                                trackableValueFactory
                                    .builder()
                                    .targetSourceIndexPath(sourceIndexPath)
                                    .contextualParameters(filteredContextParameterValues)
                                    .graphQLOutputType(gqlt)
                                    .buildForInstanceOf<JsonNode>()
                                    .toMono()
                            }
                    }
            }
            .cache()
    }

    private fun filterMaterializedParameterValuesByPathRelatedToTargetSourceIndexPath(
        sourceIndexPath: SchematicPath,
        retrievalFunctionSpec: RetrievalFunctionSpec,
        graphPhase: RequestParameterMaterializationGraphPhase,
        session: GraphQLSingleRequestSession
    ): Mono<ImmutableMap<SchematicPath, JsonNode>> {
        return Mono.fromSupplier {
            retrievalFunctionSpec.parameterVerticesByPath.keys
                .parallelStream()
                .flatMap { paramPath -> graphPhase.requestGraph.getEdgesTo(paramPath) }
                .map { edge -> edge.id.first }
                .flatMap { sourceIndPath -> graphPhase.requestGraph.getEdgesTo(sourceIndPath) }
                .recurse { edge ->
                    when {
                        graphPhase.materializedParameterValuesByPath.containsKey(edge.id.first) -> {
                            Stream.of(edge.id.first.right())
                        }
                        else -> {
                            graphPhase.requestGraph.getEdgesTo(edge.id.first).map { parentEdge ->
                                parentEdge.left()
                            }
                        }
                    }
                }
                .map { paramPath ->
                    graphPhase.materializedParameterValuesByPath.getOrNone(paramPath).map { jn ->
                        paramPath to jn
                    }
                }
                .flatMapOptions()
                .sorted(Comparator.comparing(Pair<SchematicPath, JsonNode>::first))
                .reducePairsToPersistentMap()
        }
    }

    private fun createExternalDataSourceJsonValuesRetrieverForRetrievalFunctionSpec(
        retrievalFunctionSpec: RetrievalFunctionSpec
    ): Try<ExternalDataSourceJsonValuesRetriever> {
        return retrievalFunctionSpec.sourceVerticesByPath
            .asSequence()
            .fold(
                retrievalFunctionSpec.parameterVerticesByPath.asSequence().fold(
                    schematicPathBasedJsonRetrievalFunctionFactory
                        .externalDataSourceJsonValuesRetrieverBuilder()
                        .dataSource(retrievalFunctionSpec.dataSource)
                ) { retrievalFunctionBuilder, (_, paramVert) ->
                    retrievalFunctionBuilder.addRequestParameter(paramVert)
                }
            ) { retrievalFunctionBuilder, (_, srcVert) ->
                retrievalFunctionBuilder.addSourceTarget(srcVert)
            }
            .build()
    }

    private fun createBackupSingleSourceIndexJsonOptionRetrievalFunctionFor(
        sourceIndexPath: SchematicPath,
        retrievalSpec: RetrievalFunctionSpec,
        multiSrcIndJsonRetrFunc: ExternalDataSourceJsonValuesRetriever,
        materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>,
        session: GraphQLSingleRequestSession
    ): BackupExternalDataSourceCalculatedJsonValueRetriever {
        return BackupExternalDataSourceCalculatedJsonValueRetriever {
            trackableValue: TrackableValue<JsonNode>,
            dispatchedParams: ImmutableMap<SchematicPath, Mono<JsonNode>> ->
            Flux.merge(
                    dispatchedParams
                        .asSequence()
                        .map { (p, m) -> m.map { jn -> p to jn } }
                        .asIterable()
                )
                .reduce(persistentMapOf<SchematicPath, JsonNode>()) { pm, (k, v) -> pm.put(k, v) }
                .flatMap { inputMap ->
                    multiSrcIndJsonRetrFunc
                        .invoke(inputMap)
                        .cache()
                        .timeout(DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION)
                }
                .flatMap { resultMap -> Mono.justOrEmpty(resultMap[sourceIndexPath]) }
                .map { resultJson ->
                    when (trackableValue) {
                        is TrackableValue.PlannedValue ->
                            trackableValue.transitionToCalculated {
                                calculatedValue(resultJson!!).calculatedTimestamp(Instant.now())
                            }
                        is TrackableValue.CalculatedValue -> trackableValue
                        is TrackableValue.TrackedValue -> trackableValue
                    }
                }
                .cache()
        }
    }

    private fun convertToFailureIfAnyRemainingStillPresent(
        reqCreationContext: RequestCreationContext
    ): Try<RequestCreationContext> {
        if (reqCreationContext.remainingRetrievalFunctionSpecsBySourceIndexPath.isNotEmpty()) {
            val remainingSourceIndexPathsSet =
                reqCreationContext.remainingRetrievalFunctionSpecsBySourceIndexPath.keys
                    .joinToString(", ", "{ ", " }")
            val message: String =
                """unable to apply retrieval strategies to all retrieval_function_specs 
                    |provided in request_parameter_materialization_graph_phase: [ 
                    |remaining_retrieval_specs.source_index_paths: ${remainingSourceIndexPathsSet} 
                    |]""".flatten()
            return Try.failure(
                MaterializerException(MaterializerErrorResponse.UNEXPECTED_ERROR, message)
            )
        } else {
            return Try.success(reqCreationContext)
        }
    }

    private fun checkWhetherRemainingCanBeResolved(
        reqCreationContext: RequestCreationContext,
        session: GraphQLSingleRequestSession,
        phase: RequestParameterMaterializationGraphPhase,
    ): Try<RequestCreationContext> {
        var start: Int = 0
        var end: Int = reqCreationContext.remainingRetrievalFunctionSpecsBySourceIndexPath.size
        var requestCreationContextUpdateAttempt = Try.success(reqCreationContext)
        do {
            logger.info(
                "check_whether_remaining_can_be_resolved: [ remaining_retrieval_function_spec.count: ${end} ]"
            )
            requestCreationContextUpdateAttempt =
                requestCreationContextUpdateAttempt.flatMap { outerContext ->
                    outerContext.remainingRetrievalFunctionSpecsBySourceIndexPath.asSequence().fold(
                        Try.success(outerContext)
                    ) { innerCtxUpdateAttempt, (sourceIndexPath, retrievalFunctionSpec),
                        ->
                        innerCtxUpdateAttempt.flatMap { innerContext ->
                            createDependentRetrievalFunctionsForApplicableRemainingRetrievalFunctionSpecs(
                                session,
                                phase,
                                innerContext,
                                sourceIndexPath,
                                retrievalFunctionSpec
                            )
                        }
                    }
                }
            start = end
            end =
                requestCreationContextUpdateAttempt
                    .map { ctx -> ctx.remainingRetrievalFunctionSpecsBySourceIndexPath.size }
                    .orElse(start)
        } while (start > 0 && start > end && requestCreationContextUpdateAttempt.isSuccess())
        return requestCreationContextUpdateAttempt
    }

    private fun createDependentRetrievalFunctionsForApplicableRemainingRetrievalFunctionSpecs(
        session: GraphQLSingleRequestSession,
        phase: RequestParameterMaterializationGraphPhase,
        requestCreationContext: RequestCreationContext,
        sourceIndexPath: SchematicPath,
        retrievalFunctionSpec: RetrievalFunctionSpec,
    ): Try<RequestCreationContext> {
        logger.debug(
            "create_dependent_retrieval_functions_for_applicable_remaining_retrieval_function_specs: [ source_index_path: ${sourceIndexPath} ]"
        )
        return when {
            // case 1: a multi-source index retrieval function can be built for this datasource
            retrievalFunctionSpec.parameterVerticesByPath.all { (p, _) ->
                requestCreationContext.processedRetrievalFunctionSpecsBySourceIndexPath.values
                    .any { spec -> spec.sourceVerticesByPath.containsKey(p) }
            } &&
                schematicPathBasedJsonRetrievalFunctionFactory
                    .canBuildExternalDataSourceJsonValuesRetrieverForDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                createExternalDataSourceJsonValuesRetrieverForRetrievalFunctionSpec(
                        retrievalFunctionSpec
                    )
                    .map { multiSrcIndJsonRetrievalFunction ->
                        phase.parameterIndexPathsBySourceIndexPath
                            .getOrNone(sourceIndexPath)
                            .map(PersistentSet<SchematicPath>::stream)
                            .fold(::empty, ::identity)
                            .flatMap { paramPath ->
                                phase.requestGraph
                                    .getEdgesTo(paramPath)
                                    .map { requestParameterEdge ->
                                        requestParameterEdge
                                            .some()
                                            .filterIsInstance<DependentValueRequestParameterEdge>()
                                    }
                                    .flatMapOptions()
                            }
                            .map { dependentValueRequestParameterEdge ->
                                requestCreationContext.multiSrcIndexFunctionBySourceIndexPath
                                    .asSequence()
                                    .firstOrNull { (_, func) ->
                                        func.sourcePaths.contains(
                                            dependentValueRequestParameterEdge.id.first
                                        )
                                    }
                                    .toOption()
                                    .flatMap { (srcIndPath, _) ->
                                        requestCreationContext
                                            .dispatchedMultiValueResponsesBySourceIndexPath
                                            .getOrNone(srcIndPath)
                                            .map { dispatchedMultiValueResponse ->
                                                dispatchedMultiValueResponse.map { resultMap ->
                                                    dependentValueRequestParameterEdge.id.second to
                                                        dependentValueRequestParameterEdge
                                                            .extractionFunction
                                                            .invoke(resultMap)
                                                            .getOrElse {
                                                                JsonNodeFactory.instance.nullNode()
                                                            }
                                                }
                                            }
                                    }
                            }
                            .flatMapOptions()
                            .let { entryPublisherStream ->
                                Flux.fromStream(entryPublisherStream)
                                    .collectList()
                                    .flatMapMany { entryPublishers -> Flux.merge(entryPublishers) }
                                    .reduce(persistentMapOf<SchematicPath, JsonNode>()) { pm, pair
                                        ->
                                        pm.put(pair.first, pair.second)
                                    }
                            }
                            .flatMap { inputMap ->
                                multiSrcIndJsonRetrievalFunction
                                    .invoke(inputMap)
                                    .cache()
                                    .timeout(DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION)
                            }
                            .let { deferredResult ->
                                requestCreationContext.copy(
                                    processedRetrievalFunctionSpecsBySourceIndexPath =
                                        requestCreationContext
                                            .processedRetrievalFunctionSpecsBySourceIndexPath
                                            .put(sourceIndexPath, retrievalFunctionSpec),
                                    multiSrcIndexFunctionBySourceIndexPath =
                                        requestCreationContext
                                            .multiSrcIndexFunctionBySourceIndexPath
                                            .put(sourceIndexPath, multiSrcIndJsonRetrievalFunction),
                                    dispatchedMultiValueResponsesBySourceIndexPath =
                                        requestCreationContext
                                            .dispatchedMultiValueResponsesBySourceIndexPath
                                            .put(sourceIndexPath, deferredResult),
                                    remainingRetrievalFunctionSpecsBySourceIndexPath =
                                        requestCreationContext
                                            .remainingRetrievalFunctionSpecsBySourceIndexPath
                                            .remove(sourceIndexPath)
                                )
                            }
                    }
            }
            else -> {
                Try.success(
                    requestCreationContext.copy(
                        remainingRetrievalFunctionSpecsBySourceIndexPath =
                            requestCreationContext.remainingRetrievalFunctionSpecsBySourceIndexPath
                                .put(sourceIndexPath, retrievalFunctionSpec)
                    )
                )
            }
        }
    }

    private fun createGraphStr(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    ): String {
        val edgeToString: (RequestParameterEdge) -> String = { e ->
            StandardNamingConventions.SNAKE_CASE.deriveName(e::class.simpleName!!).qualifiedForm +
                "(" +
                (when (e) {
                    is DependentValueRequestParameterEdge -> {
                        ""
                    }
                    is MaterializedValueRequestParameterEdge -> {
                        ""
                    }
                    else -> ""
                }) +
                ")"
        }
        return "%s\n%s".format(
            graph.verticesByPath.keys
                .asSequence()
                .joinToString(separator = ",\n", prefix = "vertices: { ", postfix = " }"),
            graph
                .edgesAsStream()
                .asSequence()
                .joinToString(
                    separator = ",\n",
                    prefix = "edges: { ",
                    postfix = " }",
                    transform = { e ->
                        "%s --> %s --> %s".format(e.id.first, edgeToString.invoke(e), e.id.second)
                    }
                )
        )
    }

    private fun wireBackupFunctionsToCachedSingleValueRequestDispatches(
        requestParameterMaterializationGraphPhase: RequestParameterMaterializationGraphPhase,
        requestCreationContext: RequestCreationContext
    ): Try<RequestCreationContext> {
        var index: Int = 0
        return requestCreationContext.singleSrcIndexCacheFunctionBySourceIndexPath
            .asSequence()
            .sortedWith(
                dependentFunctionsResolvedLastComparator(
                    requestParameterMaterializationGraphPhase,
                    requestCreationContext
                )
            )
            .fold(Try.success(requestCreationContext)) {
                requestCreationContextUpdateAttempt,
                (path, singSrcIndCachFunc) ->
                logger.info(
                    "wire_backup_functions_to_cached_single_value_request_dispatches: [{}]: path: {}",
                    index++,
                    path
                )
                requestCreationContextUpdateAttempt.flatMap { ctx ->
                    ctx.dispatchedTrackableValueResponsesBySourceIndexPath[path]
                        .toOption()
                        .zip(
                            ctx.multiSrcIndexFunctionBySourceIndexPath[path].toOption(),
                            ctx.singleSrcIndexBackupFunctionBySourceIndexPath[path].toOption()
                        ) { deferredResult, multiSrcIndRetrFunc, backupFunc ->
                            dispatchSingleSourceIndexCacheRetrievalWithBackupFollowup(
                                requestParameterMaterializationGraphPhase,
                                ctx,
                                deferredResult,
                                singSrcIndCachFunc,
                                multiSrcIndRetrFunc,
                                backupFunc
                            )
                        }
                        .successIfDefined(
                            singleSourceIndexCacheFunctionMissingOtherComponentsExceptionSupplier()
                        )
                        .map { updatedDeferredResult ->
                            ctx.copy(
                                dispatchedTrackableValueResponsesBySourceIndexPath =
                                    ctx.dispatchedTrackableValueResponsesBySourceIndexPath.put(
                                        path,
                                        updatedDeferredResult
                                    )
                            )
                        }
                }
            }
    }

    private fun dependentFunctionsResolvedLastComparator(
        requestParameterMaterializationGraphPhase: RequestParameterMaterializationGraphPhase,
        requestCreationContext: RequestCreationContext
    ): Comparator<Map.Entry<SchematicPath, TrackableJsonValueRetriever>> {
        return Comparator { e1, e2 ->
            requestCreationContext.multiSrcIndexFunctionBySourceIndexPath[e1.key]
                .toOption()
                .zip(
                    requestCreationContext.multiSrcIndexFunctionBySourceIndexPath[e2.key].toOption()
                )
                .map { (m1, m2) ->
                    when {
                        m1.parameterPaths
                            .parallelStream()
                            .flatMap { paramPath ->
                                requestParameterMaterializationGraphPhase.requestGraph.getEdgesTo(
                                    paramPath
                                )
                            }
                            .map { edge -> edge.id.first }
                            .anyMatch { srcIndPath -> srcIndPath in m2.sourcePaths } -> 1
                        m2.parameterPaths
                            .parallelStream()
                            .flatMap { paramPath ->
                                requestParameterMaterializationGraphPhase.requestGraph.getEdgesTo(
                                    paramPath
                                )
                            }
                            .map { edge -> edge.id.first }
                            .anyMatch { srcIndPath -> srcIndPath in m1.sourcePaths } -> -1
                        else -> 0
                    }
                }
                .getOrElse { 0 }
        }
    }

    private fun singleSourceIndexCacheFunctionMissingOtherComponentsExceptionSupplier():
        () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """one or more of the other components created 
                    |at the same time as the singleSourceIndexJsonOptionCacheRetrievalFunction 
                    |is/are missing""".flatten()
            )
        }
    }

    private fun dispatchSingleSourceIndexCacheRetrievalWithBackupFollowup(
        phase: RequestParameterMaterializationGraphPhase,
        requestCreationContext: RequestCreationContext,
        dispatchedTrackableValueRequest: Mono<TrackableValue<JsonNode>>,
        trackableJsonValueRetriever: TrackableJsonValueRetriever,
        externalDataSourceJsonValuesRetriever: ExternalDataSourceJsonValuesRetriever,
        backupExternalDataSourceCalculatedJsonValueRetriever:
            BackupExternalDataSourceCalculatedJsonValueRetriever
    ): Mono<TrackableValue<JsonNode>> {
        val deferredParameterValuesByParamPath: ImmutableMap<SchematicPath, Mono<JsonNode>> =
            externalDataSourceJsonValuesRetriever.parameterPaths
                .stream()
                // TODO: handle other edge types that could be mapped to params: materialized value
                // edges
                // currently no use cases have them
                .flatMap { paramPath -> phase.requestGraph.getEdgesTo(paramPath) }
                .filter { edge -> edge is DependentValueRequestParameterEdge }
                .map { edge -> edge as DependentValueRequestParameterEdge }
                .map { edge -> edge.id.first to edge }
                .flatMap { (srcIndPath, edge) ->
                    requestCreationContext.multiSrcIndexFunctionBySourceIndexPath
                        .streamPairs()
                        .filter { (_, multSrcRetrFunc) ->
                            multSrcRetrFunc.sourcePaths.contains(srcIndPath)
                        }
                        .map { (p, _) ->
                            when {
                                requestCreationContext
                                    .dispatchedTrackableValueResponsesBySourceIndexPath
                                    .containsKey(p) -> {
                                    requestCreationContext
                                        .dispatchedTrackableValueResponsesBySourceIndexPath[p]!!
                                        .flatMap { resultOpt ->
                                            resultOpt.fold(
                                                { pv -> Mono.empty() },
                                                { cv -> Mono.justOrEmpty(cv.calculatedValue) },
                                                { tv -> Mono.justOrEmpty(tv.trackedValue) }
                                            )
                                        }
                                        .left()
                                        .some()
                                }
                                requestCreationContext
                                    .dispatchedMultiValueResponsesBySourceIndexPath
                                    .containsKey(p) -> {
                                    requestCreationContext
                                        .dispatchedMultiValueResponsesBySourceIndexPath[p]!!
                                        .right()
                                        .map { multiSrcIndexDeferredResult ->
                                            multiSrcIndexDeferredResult.flatMap { resultMap ->
                                                edge.extractionFunction.invoke(resultMap).toMono()
                                            }
                                        }
                                        .some()
                                }
                                else -> {
                                    logger.warn(
                                        """edge: [ ${edge.id} ] could not be applied for 
                                        |wiring a parameter value to a function 
                                        |[ path: ${p} ] potentially impacting retrieval/calculation of 
                                        |[ ${srcIndPath} ]""".flatten()
                                    )
                                    none()
                                }
                            }
                        }
                        .flatMapOptions()
                        .map { deferredSingleValueResult ->
                            edge.id.second to deferredSingleValueResult.fold(::identity, ::identity)
                        }
                }
                .reducePairsToPersistentMap()
        return dispatchedTrackableValueRequest
            .cache()
            .flatMap { tv: TrackableValue<JsonNode> ->
                when (tv) {
                    is TrackableValue.PlannedValue -> {
                        backupExternalDataSourceCalculatedJsonValueRetriever.invoke(
                            tv,
                            deferredParameterValuesByParamPath
                        )
                    }
                    is TrackableValue.CalculatedValue -> Mono.just(tv)
                    is TrackableValue.TrackedValue -> Mono.just(tv)
                }
            }
            .cache()
    }

    private fun convertRequestCreationContextIntoRequestDispatchMaterializationPhase(
        requestCreationContext: RequestCreationContext
    ): RequestDispatchMaterializationPhase {
        return requestCreationContext.processedRetrievalFunctionSpecsBySourceIndexPath
            .asSequence()
            .fold(
                persistentListOf<TrackableSingleJsonValueDispatch>() to
                    persistentListOf<ExternalDataSourceValuesDispatch>()
            ) { plPair, (sourceIndexPath, retrievalSpec) ->
                when {
                    sourceIndexPath in
                        requestCreationContext
                            .dispatchedTrackableValueResponsesBySourceIndexPath -> {
                        plPair.first.add(
                            sourceIndexRequestDispatchFactory
                                .builder()
                                .sourceIndexPath(sourceIndexPath)
                                .retrievalFunctionSpec(retrievalSpec)
                                .trackableJsonValueRetriever(
                                    requestCreationContext
                                        .singleSrcIndexCacheFunctionBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .backUpExternalDataSourceCalculatedJsonValueRetriever(
                                    requestCreationContext
                                        .singleSrcIndexBackupFunctionBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .dispatchedTrackableValueJsonRequest(
                                    requestCreationContext
                                        .dispatchedTrackableValueResponsesBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .backupBaseExternalDataSourceJsonValuesRetriever(
                                    requestCreationContext.multiSrcIndexFunctionBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .build()
                        ) to plPair.second
                    }
                    sourceIndexPath in
                        requestCreationContext.dispatchedMultiValueResponsesBySourceIndexPath -> {
                        plPair.first to
                            plPair.second.add(
                                sourceIndexRequestDispatchFactory
                                    .builder()
                                    .sourceIndexPath(sourceIndexPath)
                                    .retrievalFunctionSpec(retrievalSpec)
                                    .externalDataSourceJsonValuesRetriever(
                                        requestCreationContext
                                            .multiSrcIndexFunctionBySourceIndexPath[
                                                sourceIndexPath]!!
                                    )
                                    .dispatchedMultipleIndexRequest(
                                        requestCreationContext
                                            .dispatchedMultiValueResponsesBySourceIndexPath[
                                                sourceIndexPath]!!
                                    )
                                    .build()
                            )
                    }
                    else -> {
                        plPair
                    }
                }
            }
            .let { cacheableSingleOrMultiSrcRetrievals ->
                DefaultRequestDispatchMaterializationPhase(
                    cacheableSingleOrMultiSrcRetrievals.first
                        .asSequence()
                        .map { s -> s.sourceIndexPath to s }
                        .reducePairsToPersistentMap(),
                    cacheableSingleOrMultiSrcRetrievals.second
                        .asSequence()
                        .map { m -> m.sourceIndexPath to m }
                        .reducePairsToPersistentMap()
                )
            }
    }
}
