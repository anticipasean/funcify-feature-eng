package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.datasource.retrieval.BackupSingleSourceIndexJsonOptionRetrievalFunction
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.*
import funcify.feature.materializer.service.SourceIndexRequestDispatch.*
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.DeferredExtensions.toDeferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.stream.Stream.empty
import kotlin.streams.asSequence
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationDispatchService(
    private val schematicPathBasedJsonRetrievalFunctionFactory:
        SchematicPathBasedJsonRetrievalFunctionFactory,
    private val sourceIndexRequestDispatchFactory: SourceIndexRequestDispatchFactory =
        DefaultSourceIndexRequestDispatchFactory()
) : SingleRequestMaterializationDispatchService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationDispatchService>()

        private data class RequestCreationContext(
            val processedRetrievalFunctionSpecsBySourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf(),
            val remainingRetrievalFunctionSpecsBySourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf(),
            val multiSrcIndexFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, MultipleSourceIndicesJsonRetrievalFunction> =
                persistentMapOf(),
            val singleSrcIndexCacheFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, SingleSourceIndexJsonOptionCacheRetrievalFunction> =
                persistentMapOf(),
            val singleSrcIndexBackupFunctionBySourceIndexPath:
                PersistentMap<SchematicPath, BackupSingleSourceIndexJsonOptionRetrievalFunction> =
                persistentMapOf(),
            val dispatchedMultiValueDeferredResponsesBySourceIndexPath:
                PersistentMap<SchematicPath, Deferred<ImmutableMap<SchematicPath, JsonNode>>> =
                persistentMapOf(),
            val dispatchedSingleValueDeferredResponsesBySourceIndexPath:
                PersistentMap<SchematicPath, Deferred<Option<JsonNode>>> =
                persistentMapOf()
        )
    }

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: SingleRequestFieldMaterializationSession
    ): Try<SingleRequestFieldMaterializationSession> {
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
            .map { reqCreationContext ->
                convertRequestCreationContextIntoRequestDispatchMaterializationPhase(
                    reqCreationContext
                )
            }
            .map { requestDispatchPhase ->
                session.update { requestDispatchMaterializationPhase(requestDispatchPhase) }
            }
    }

    private fun createAndDispatchFirstRoundOfRequestFunctionsForApplicableRetrievalFunctionSpecs(
        session: SingleRequestFieldMaterializationSession,
        phase: RequestParameterMaterializationGraphPhase,
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
                p in phase.materializedParameterValuesByPath
            } &&
                schematicPathBasedJsonRetrievalFunctionFactory
                    .canBuildMultipleSourceIndicesJsonRetrievalFunctionForDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                createMultipleSourceIndicesJsonRetrievalFunctionForRetrievalFunctionSpec(
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
                            dispatchedMultiValueDeferredResponsesBySourceIndexPath =
                                requestCreationContext
                                    .dispatchedMultiValueDeferredResponsesBySourceIndexPath
                                    .put(
                                        sourceIndexPath,
                                        multiSrcIndJsonRetrFunc.invoke(
                                            retrievalFunctionSpec.parameterVerticesByPath.keys
                                                .asSequence()
                                                .map { p ->
                                                    phase.materializedParameterValuesByPath[p]
                                                        .toOption()
                                                        .map { jn -> p to jn }
                                                }
                                                .flatMapOptions()
                                                .reducePairsToPersistentMap()
                                        )
                                    )
                        )
                    }
            }
            // case 2: source_index_path represents a single scalar or list (non-object) value that
            // may be provided
            // by a caching function and a function can be built for this datasource/ datasource
            // caching service
            retrievalFunctionSpec.sourceVerticesByPath
                .asIterable()
                .singleOrNone()
                .filter { (_, srcJunctionOrLeafVertex) -> srcJunctionOrLeafVertex.isRight() }
                .isDefined() &&
                schematicPathBasedJsonRetrievalFunctionFactory
                    .canBuildSingleSourceIndexJsonOptionCacheRetrievalFunctionForDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                Try.fromOption(
                        retrievalFunctionSpec.sourceVerticesByPath.asIterable().singleOrNone()
                    )
                    .flatMap { (_, srcJunctionOrLeafVertex) ->
                        schematicPathBasedJsonRetrievalFunctionFactory
                            .singleSourceIndexCacheRetrievalFunctionBuilder()
                            .cacheForDataSource(retrievalFunctionSpec.dataSource)
                            .sourceTarget(srcJunctionOrLeafVertex)
                            .build()
                    }
                    .zip(
                        createBackupSingleSourceIndexJsonOptionRetrievalFunctionFor(
                            sourceIndexPath,
                            retrievalFunctionSpec
                        )
                    )
                    .map {
                        (
                            singleSrcIndCacheRetrFunc:
                                SingleSourceIndexJsonOptionCacheRetrievalFunction,
                            backupSingleSrcIndRetrFunc:
                                BackupSingleSourceIndexJsonOptionRetrievalFunction
                        ) ->
                        requestCreationContext.copy(
                            processedRetrievalFunctionSpecsBySourceIndexPath =
                                requestCreationContext
                                    .processedRetrievalFunctionSpecsBySourceIndexPath
                                    .put(sourceIndexPath, retrievalFunctionSpec),
                            singleSrcIndexCacheFunctionBySourceIndexPath =
                                requestCreationContext.singleSrcIndexCacheFunctionBySourceIndexPath
                                    .put(sourceIndexPath, singleSrcIndCacheRetrFunc),
                            singleSrcIndexBackupFunctionBySourceIndexPath =
                                requestCreationContext.singleSrcIndexBackupFunctionBySourceIndexPath
                                    .put(sourceIndexPath, backupSingleSrcIndRetrFunc),
                            dispatchedSingleValueDeferredResponsesBySourceIndexPath =
                                requestCreationContext
                                    .dispatchedSingleValueDeferredResponsesBySourceIndexPath
                                    .put(
                                        sourceIndexPath,
                                        singleSrcIndCacheRetrFunc.invoke(
                                            // TODO: Explore whether all "context" values should be
                                            // included
                                            // or only some
                                            phase.materializedParameterValuesByPath
                                        )
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

    private fun createMultipleSourceIndicesJsonRetrievalFunctionForRetrievalFunctionSpec(
        retrievalFunctionSpec: RetrievalFunctionSpec
    ): Try<MultipleSourceIndicesJsonRetrievalFunction> {
        return retrievalFunctionSpec.sourceVerticesByPath
            .asSequence()
            .fold(
                retrievalFunctionSpec.parameterVerticesByPath.asSequence().fold(
                    schematicPathBasedJsonRetrievalFunctionFactory
                        .multipleSourceIndicesJsonRetrievalFunctionBuilder()
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
        retrievalSpec: RetrievalFunctionSpec
    ): Try<BackupSingleSourceIndexJsonOptionRetrievalFunction> {
        return createMultipleSourceIndicesJsonRetrievalFunctionForRetrievalFunctionSpec(
                retrievalSpec
            )
            .map { multSrcIndJsonRetrFunc ->
                BackupSingleSourceIndexJsonOptionRetrievalFunction { dispatchedParams ->
                    Deferred.deferredSequence(
                            dispatchedParams.asSequence().map { (p, jnOptDef) ->
                                jnOptDef.map { jnOpt ->
                                    p to jnOpt.getOrElse { JsonNodeFactory.instance.nullNode() }
                                }
                            }
                        )
                        .toMono()
                        .map { pairs -> pairs.asSequence().reducePairsToPersistentMap() }
                        .toDeferred()
                        .flatMap { inputMap -> multSrcIndJsonRetrFunc.invoke(inputMap) }
                        .map { resultMap ->
                            resultMap.getOrNone(sourceIndexPath).getOrElse {
                                JsonNodeFactory.instance.nullNode()
                            }
                        }
                }
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
        session: SingleRequestFieldMaterializationSession,
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
        session: SingleRequestFieldMaterializationSession,
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
                    .canBuildMultipleSourceIndicesJsonRetrievalFunctionForDataSource(
                        retrievalFunctionSpec.dataSource.key
                    ) -> {
                createMultipleSourceIndicesJsonRetrievalFunctionForRetrievalFunctionSpec(
                        retrievalFunctionSpec
                    )
                    .map { multiSrcIndJsonRetrievalFunction ->
                        phase.parameterIndexPathsBySourceIndexPath
                            .getOrNone(sourceIndexPath)
                            .map(PersistentSet<SchematicPath>::stream)
                            .fold(::empty, ::identity)
                            .parallel()
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
                                            .dispatchedMultiValueDeferredResponsesBySourceIndexPath
                                            .getOrNone(srcIndPath)
                                            .map { resultDeferred ->
                                                resultDeferred.map { resultMap ->
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
                            .let { pairStream ->
                                Deferred.deferredStream(pairStream)
                                    .toMono()
                                    .map { pairs ->
                                        pairs.asSequence().reducePairsToPersistentMap()
                                    }
                                    .toDeferred()
                            }
                            .flatMap { inputMap ->
                                multiSrcIndJsonRetrievalFunction.invoke(inputMap)
                            }
                            .let { resultDeferred ->
                                requestCreationContext.copy(
                                    processedRetrievalFunctionSpecsBySourceIndexPath =
                                        requestCreationContext
                                            .processedRetrievalFunctionSpecsBySourceIndexPath
                                            .put(sourceIndexPath, retrievalFunctionSpec),
                                    multiSrcIndexFunctionBySourceIndexPath =
                                        requestCreationContext
                                            .multiSrcIndexFunctionBySourceIndexPath
                                            .put(sourceIndexPath, multiSrcIndJsonRetrievalFunction),
                                    dispatchedMultiValueDeferredResponsesBySourceIndexPath =
                                        requestCreationContext
                                            .dispatchedMultiValueDeferredResponsesBySourceIndexPath
                                            .put(sourceIndexPath, resultDeferred),
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

    private fun convertRequestCreationContextIntoRequestDispatchMaterializationPhase(
        requestCreationContext: RequestCreationContext
    ): RequestDispatchMaterializationPhase {
        return requestCreationContext.processedRetrievalFunctionSpecsBySourceIndexPath
            .asSequence()
            .fold(
                persistentListOf<DispatchedCacheableSingleSourceIndexRetrieval>() to
                    persistentListOf<DispatchedMultiSourceIndexRetrieval>()
            ) { plPair, (sourceIndexPath, retrievalSpec) ->
                when {
                    sourceIndexPath in
                        requestCreationContext
                            .dispatchedSingleValueDeferredResponsesBySourceIndexPath -> {
                        plPair.first.add(
                            sourceIndexRequestDispatchFactory
                                .builder()
                                .sourceIndexPath(sourceIndexPath)
                                .retrievalFunctionSpec(retrievalSpec)
                                .singleSourceIndexJsonOptionCacheRetrievalFunction(
                                    requestCreationContext
                                        .singleSrcIndexCacheFunctionBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .backupSingleSourceIndexJsonOptionRetrievalFunction(
                                    requestCreationContext
                                        .singleSrcIndexBackupFunctionBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .dispatchedSingleIndexCacheRequest(
                                    requestCreationContext
                                        .dispatchedSingleValueDeferredResponsesBySourceIndexPath[
                                            sourceIndexPath]!!
                                )
                                .build()
                        ) to plPair.second
                    }
                    sourceIndexPath in
                        requestCreationContext
                            .dispatchedMultiValueDeferredResponsesBySourceIndexPath -> {
                        plPair.first to
                            plPair.second.add(
                                sourceIndexRequestDispatchFactory
                                    .builder()
                                    .sourceIndexPath(sourceIndexPath)
                                    .retrievalFunctionSpec(retrievalSpec)
                                    .multipleSourceIndicesJsonRetrievalFunction(
                                        requestCreationContext
                                            .multiSrcIndexFunctionBySourceIndexPath[
                                                sourceIndexPath]!!
                                    )
                                    .dispatchedMultipleIndexRequest(
                                        requestCreationContext
                                            .dispatchedMultiValueDeferredResponsesBySourceIndexPath[
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
