package funcify.feature.materializer.graph

import arrow.core.getOrElse
import arrow.core.none
import arrow.core.toOption
import com.google.common.cache.CacheBuilder
import funcify.feature.error.ServiceError
import funcify.feature.graph.PersistentGraphFactory
import funcify.feature.materializer.graph.component.DefaultQueryComponentContextFactory
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.graph.connector.StandardQueryConnector
import funcify.feature.materializer.graph.connector.StandardQueryTraverser
import funcify.feature.materializer.graph.connector.TabularQueryConnector
import funcify.feature.materializer.graph.connector.TabularQueryRawInputBasedOperationCreator
import funcify.feature.materializer.graph.connector.TabularQueryVariableBasedOperationCreator
import funcify.feature.materializer.graph.context.DefaultRequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import java.time.Duration
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestMaterializationGraphService(
    private val requestMaterializationGraphContextFactory:
        RequestMaterializationGraphContextFactory =
        DefaultRequestMaterializationGraphContextFactory,
    private val queryComponentContextFactory: QueryComponentContextFactory =
        DefaultQueryComponentContextFactory
) : SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationGraphService>()
    }

    private val requestMaterializationGraphCache:
        ConcurrentMap<RequestMaterializationGraphCacheKey, RequestMaterializationGraph> by lazy {
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .build<RequestMaterializationGraphCacheKey, RequestMaterializationGraph>()
            .asMap()
    }

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession> {
        logger.debug(
            "create_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return when {
            session.requestMaterializationGraph.isDefined() -> {
                Mono.just(session)
            }
            else -> {
                Mono.defer { createRequestMaterializationGraphCacheKeyForSession(session) }
                    .flatMap { rmgck: RequestMaterializationGraphCacheKey ->
                        when (
                            val rmg: RequestMaterializationGraph? =
                                requestMaterializationGraphCache[rmgck]
                        ) {
                            null -> {
                                calculateRequestMaterializationGraphForSession(rmgck, session)
                                    .map { rmg1: RequestMaterializationGraph ->
                                        requestMaterializationGraphCache[rmgck] = rmg1
                                        session.update {
                                            preparsedDocumentEntry(
                                                PreparsedDocumentEntry(rmg1.document)
                                            )
                                            requestMaterializationGraph(rmg1)
                                        }
                                    }
                            }
                            else -> {
                                Mono.just(
                                    session.update {
                                        preparsedDocumentEntry(PreparsedDocumentEntry(rmg.document))
                                        requestMaterializationGraph(rmg)
                                    }
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun createRequestMaterializationGraphCacheKeyForSession(
        session: GraphQLSingleRequestSession
    ): Mono<out RequestMaterializationGraphCacheKey> {
        logger.info(
            "create_request_materialization_graph_cache_key_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return when {
                session.preparsedDocumentEntry
                    .filterNot(PreparsedDocumentEntry::hasErrors)
                    .mapNotNull(PreparsedDocumentEntry::getDocument)
                    .isDefined() -> {
                    RequestMaterializationGraphCacheKey(
                            materializationMetamodelCreated =
                                session.materializationMetamodel.created,
                            variableKeys =
                                session.rawGraphQLRequest.variables.keys.toPersistentSet(),
                            rawInputContextKeys =
                                session.rawInputContext.fold(
                                    ::persistentSetOf,
                                    RawInputContext::fieldNames
                                ),
                            operationName = session.rawGraphQLRequest.operationName.toOption(),
                            standardQueryDocument =
                                session.preparsedDocumentEntry.mapNotNull(
                                    PreparsedDocumentEntry::getDocument
                                ),
                            tabularQueryOutputColumns = none(),
                        )
                        .successIfNonNull()
                }
                session.preparsedDocumentEntry
                    .filter(PreparsedDocumentEntry::hasErrors)
                    .isDefined() -> {
                    ServiceError.invalidRequestErrorBuilder()
                        .message(
                            """preparsed_document_entry in session [ session_id: %s ] 
                            |contains validation errors [ %s ]"""
                                .flatten(),
                            session.sessionId,
                            session.preparsedDocumentEntry
                                .mapNotNull(PreparsedDocumentEntry::getErrors)
                                .getOrElse(::emptyList)
                                .asSequence()
                                .withIndex()
                                .joinToString(", ") { (idx: Int, ge: GraphQLError) -> "[$idx]:$ge" }
                        )
                        .build()
                        .failure()
                }
                session.rawGraphQLRequest.expectedOutputFieldNames.isNotEmpty() -> {
                    RequestMaterializationGraphCacheKey(
                            materializationMetamodelCreated =
                                session.materializationMetamodel.created,
                            variableKeys =
                                session.rawGraphQLRequest.variables.keys.toPersistentSet(),
                            rawInputContextKeys =
                                session.rawInputContext.fold(
                                    ::persistentSetOf,
                                    RawInputContext::fieldNames
                                ),
                            operationName = none(),
                            standardQueryDocument = none(),
                            tabularQueryOutputColumns =
                                session.rawGraphQLRequest.expectedOutputFieldNames
                                    .toPersistentSet()
                                    .toOption(),
                        )
                        .successIfNonNull()
                }
                else -> {
                    ServiceError.invalidRequestErrorBuilder()
                        .message(
                            """session must contain either PreparsedDocumentEntry 
                            |with a valid Document  
                            |or a list of expected column names in the output 
                            |for a tabular query"""
                                .flatten()
                        )
                        .build()
                        .failure()
                }
            }
            .toMono()
    }

    private fun calculateRequestMaterializationGraphForSession(
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession
    ): Mono<out RequestMaterializationGraph> {
        logger.info(
            "calculate_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return Mono.fromCallable {
                when {
                    cacheKey.standardQueryDocument.isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .standardQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .queryComponentContextFactory(queryComponentContextFactory)
                            .variableKeys(cacheKey.variableKeys)
                            .rawInputContextKeys(cacheKey.rawInputContextKeys)
                            .operationName(cacheKey.operationName.orNull()!!)
                            .document(cacheKey.standardQueryDocument.orNull()!!)
                            .requestGraph(
                                PersistentGraphFactory.defaultFactory()
                                    .builder()
                                    .directed()
                                    .permitParallelEdges()
                                    .build()
                            )
                            .build()
                    }
                    cacheKey.tabularQueryOutputColumns.isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .tabularQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .queryComponentContextFactory(queryComponentContextFactory)
                            .variableKeys(cacheKey.variableKeys)
                            .rawInputContextKeys(cacheKey.rawInputContextKeys)
                            .outputColumnNames(cacheKey.tabularQueryOutputColumns.orNull()!!)
                            .requestGraph(
                                PersistentGraphFactory.defaultFactory()
                                    .builder()
                                    .directed()
                                    .permitParallelEdges()
                                    .build()
                            )
                            .build()
                    }
                    else -> {
                        throw ServiceError.of(
                            """unable to create a request_materialization_graph_context 
                            |instance for [ cache_key: %s ]"""
                                .flatten(),
                            cacheKey
                        )
                    }
                }
            }
            .flatMap { c: RequestMaterializationGraphContext ->
                deriveRequestMaterializationGraphFromContext(c)
            }
    }

    private fun deriveRequestMaterializationGraphFromContext(
        context: RequestMaterializationGraphContext
    ): Mono<out RequestMaterializationGraph> {
        return Mono.fromCallable {
                when (context) {
                    is StandardQuery -> {
                        val connector = StandardQueryConnector
                        StandardQueryTraverser.invoke(context).fold(context) {
                            sq: StandardQuery,
                            qcc: QueryComponentContext ->
                            when (qcc) {
                                is QueryComponentContext.FieldArgumentComponentContext -> {
                                    connector.connectFieldArgument(sq, qcc)
                                }
                                is QueryComponentContext.SelectedFieldComponentContext -> {
                                    connector.connectSelectedField(sq, qcc)
                                }
                            }
                        }
                    }
                    is TabularQuery -> {
                        val connector = TabularQueryConnector
                        when {
                            context.rawInputContextKeys.isEmpty() -> {
                                TabularQueryVariableBasedOperationCreator.invoke(context).fold(
                                    context
                                ) { tq: TabularQuery, qcc: QueryComponentContext ->
                                    when (qcc) {
                                        is QueryComponentContext.FieldArgumentComponentContext -> {
                                            connector.connectFieldArgument(tq, qcc)
                                        }
                                        is QueryComponentContext.SelectedFieldComponentContext -> {
                                            connector.connectSelectedField(tq, qcc)
                                        }
                                    }
                                }
                            }
                            else -> {
                                TabularQueryRawInputBasedOperationCreator.invoke(context).fold(
                                    context
                                ) { tq: TabularQuery, qcc: QueryComponentContext ->
                                    when (qcc) {
                                        is QueryComponentContext.FieldArgumentComponentContext -> {
                                            connector.connectFieldArgument(tq, qcc)
                                        }
                                        is QueryComponentContext.SelectedFieldComponentContext -> {
                                            connector.connectSelectedField(tq, qcc)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        throw ServiceError.of(
                            "unsupported request_materialization_graph_context.type: [ type: %s ]",
                            context::class.qualifiedName
                        )
                    }
                }
            }
            .doOnNext { rmgc: RequestMaterializationGraphContext ->
                logger.debug(
                    "generated_graph: \n{}",
                    rmgc.requestGraph.stringify(
                        vertexStringifier = { qcc: QueryComponentContext ->
                            qcc::class.simpleName ?: "<NA>"
                        }
                    )
                )
            }
            .then(
                Mono.error { ServiceError.of("request_materialization_graph not yet implemented") }
            )
    }
}
