package funcify.feature.materializer.graph

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.cache.CacheBuilder
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.input.DefaultRawInputContextShapeFactory
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import funcify.feature.tree.PersistentTree
import funcify.feature.tree.path.TreePath
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import java.time.Duration
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestMaterializationGraphService :
    SingleRequestMaterializationGraphService {

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
    ): Mono<GraphQLSingleRequestSession> {
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
                                calculateRequestMaterializationGraphForSession(session).map {
                                    rmg1: RequestMaterializationGraph ->
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
    ): Mono<RequestMaterializationGraphCacheKey> {
        logger.info(
            "create_request_materialization_graph_cache_key_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return extractRawInputContextShapeFromRawInputContextIfPresent(session)
            .flatMap { rics: Option<RawInputContextShape> ->
                when {
                    session.preparsedDocumentEntry
                        .filterNot(PreparsedDocumentEntry::hasErrors)
                        .mapNotNull(PreparsedDocumentEntry::getDocument)
                        .isDefined() -> {
                        RequestMaterializationGraphCacheKey(
                                materializationMetamodelCreated =
                                    session.materializationMetamodel.created,
                                variableKeys =
                                    session.rawGraphQLRequest.variables.keys.toPersistentSet(),
                                standardQueryDocument =
                                    session.preparsedDocumentEntry.mapNotNull(
                                        PreparsedDocumentEntry::getDocument
                                    ),
                                tabularQueryOutputColumns = none(),
                                rawInputContextShape = rics,
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
                                    .joinToString(", ") { (idx: Int, ge: GraphQLError) ->
                                        "[$idx]:$ge"
                                    }
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
                                standardQueryDocument = none(),
                                tabularQueryOutputColumns =
                                    session.rawGraphQLRequest.expectedOutputFieldNames
                                        .toPersistentSet()
                                        .toOption(),
                                rawInputContextShape = rics
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
            }
            .toMono()
            .widen()
    }

    private fun extractRawInputContextShapeFromRawInputContextIfPresent(
        session: GraphQLSingleRequestSession
    ): Try<Option<RawInputContextShape>> {
        return Try.success(
            session.rawInputContext.map { ric: RawInputContext ->
                when (ric) {
                    is RawInputContext.CommaSeparatedValues -> {
                        DefaultRawInputContextShapeFactory.createTabularShape(
                            ric.fieldNames().toPersistentSet()
                        )
                    }
                    is RawInputContext.TabularJson -> {
                        DefaultRawInputContextShapeFactory.createTabularShape(
                            ric.fieldNames().toPersistentSet()
                        )
                    }
                    is RawInputContext.StandardJson -> {
                        // TODO: Reduce to just domain nodes
                        val treePathSet: ImmutableSet<TreePath> =
                            PersistentTree.fromSequenceTraversal(ric.asJsonNode()) { jn: JsonNode ->
                                    when (jn) {
                                        is ArrayNode -> {
                                            jn.asSequence().map { j -> j.left() }
                                        }
                                        is ObjectNode -> {
                                            jn.fields().asSequence().map { e ->
                                                (e.key to e.value).right()
                                            }
                                        }
                                        else -> {
                                            emptySequence()
                                        }
                                    }
                                }
                                .asSequence()
                                .filter { (_: TreePath, jn: JsonNode) -> !jn.isContainerNode }
                                .map { (tp: TreePath, _: JsonNode) -> tp }
                                .toPersistentSet()
                        DefaultRawInputContextShapeFactory.createTreeShape(
                            treePathSet = treePathSet
                        )
                    }
                }
            }
        )
    }

    private fun calculateRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<RequestMaterializationGraph> {
        logger.info(
            "calculate_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        TODO("Not yet implemented")
    }
}
