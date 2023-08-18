package funcify.feature.materializer.graph

import arrow.core.Option
import arrow.core.filterIsInstance
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
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.graph.connector.ExpectedStandardJsonInputBasedStandardQueryConnector
import funcify.feature.materializer.graph.connector.ExpectedStandardJsonInputBasedTabularQueryConnector
import funcify.feature.materializer.graph.connector.ExpectedTabularInputBasedStandardQueryConnector
import funcify.feature.materializer.graph.connector.ExpectedTabularInputBasedTabularQueryConnector
import funcify.feature.materializer.graph.connector.RequestMaterializationGraphConnector
import funcify.feature.materializer.graph.connector.StandardQueryConnector
import funcify.feature.materializer.graph.connector.TabularQueryConnector
import funcify.feature.materializer.graph.context.ExpectedStandardJsonInputStandardQuery
import funcify.feature.materializer.graph.context.ExpectedStandardJsonInputTabularQuery
import funcify.feature.materializer.graph.context.ExpectedTabularInputStandardQuery
import funcify.feature.materializer.graph.context.ExpectedTabularInputTabularQuery
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.graph.input.DefaultRawInputContextShapeFactory
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import funcify.feature.tree.PersistentTree
import funcify.feature.tree.path.TreePath
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Argument
import graphql.language.Field
import graphql.language.Node
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
internal class DefaultSingleRequestMaterializationGraphService(
    private val requestMaterializationGraphContextFactory:
        RequestMaterializationGraphContextFactory = object : RequestMaterializationGraphContextFactory {
        override fun expectedStandardJsonInputStandardQueryBuilder(): ExpectedStandardJsonInputStandardQuery.Builder {
            TODO("Not yet implemented")
        }

        override fun expectedStandardJsonInputTabularQueryBuilder(): ExpectedStandardJsonInputTabularQuery.Builder {
            TODO("Not yet implemented")
        }

        override fun expectedTabularInputStandardQueryBuilder(): ExpectedTabularInputStandardQuery.Builder {
            TODO("Not yet implemented")
        }

        override fun expectedTabularInputTabularQueryBuilder(): ExpectedTabularInputTabularQuery.Builder {
            TODO("Not yet implemented")
        }

        override fun standardQueryBuilder(): StandardQuery.Builder {
            TODO("Not yet implemented")
        }

        override fun tabularQueryBuilder(): TabularQuery.Builder {
            TODO("Not yet implemented")
        }

    },
    private val queryComponentContextFactory: QueryComponentContextFactory = object : QueryComponentContextFactory {
        override fun selectedFieldComponentContextBuilder(): QueryComponentContext.SelectedFieldComponentContext.Builder {
            TODO("Not yet implemented")
        }

        override fun fieldArgumentComponentContextBuilder(): QueryComponentContext.FieldArgumentComponentContext.Builder {
            TODO("Not yet implemented")
        }

    }
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
                                operationName = session.rawGraphQLRequest.operationName.toOption(),
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
                                operationName = none(),
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
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession
    ): Mono<RequestMaterializationGraph> {
        logger.info(
            "calculate_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return Mono.fromCallable {
                when {
                    cacheKey.standardQueryDocument.isDefined() &&
                        cacheKey.rawInputContextShape
                            .filterIsInstance<RawInputContextShape.Tree>()
                            .isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .expectedStandardJsonInputStandardQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .document(cacheKey.standardQueryDocument.orNull()!!)
                            .standardJsonShape(
                                cacheKey.rawInputContextShape.orNull() as RawInputContextShape.Tree
                            )
                            .build()
                    }
                    cacheKey.standardQueryDocument.isDefined() &&
                        cacheKey.rawInputContextShape
                            .filterIsInstance<RawInputContextShape.Tabular>()
                            .isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .expectedTabularInputStandardQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .outputColumnNames(cacheKey.tabularQueryOutputColumns.orNull()!!)
                            .tabularShape(
                                cacheKey.rawInputContextShape.orNull()
                                    as RawInputContextShape.Tabular
                            )
                            .build()
                    }
                    cacheKey.tabularQueryOutputColumns.isDefined() &&
                        cacheKey.rawInputContextShape
                            .filterIsInstance<RawInputContextShape.Tabular>()
                            .isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .expectedTabularInputTabularQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .outputColumnNames(cacheKey.tabularQueryOutputColumns.orNull()!!)
                            .tabularShape(
                                cacheKey.rawInputContextShape.orNull()
                                    as RawInputContextShape.Tabular
                            )
                            .build()
                    }
                    cacheKey.tabularQueryOutputColumns.isDefined() &&
                        cacheKey.rawInputContextShape
                            .filterIsInstance<RawInputContextShape.Tree>()
                            .isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .expectedStandardJsonInputTabularQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .outputColumnNames(cacheKey.tabularQueryOutputColumns.orNull()!!)
                            .standardJsonShape(
                                cacheKey.rawInputContextShape.orNull() as RawInputContextShape.Tree
                            )
                            .build()
                    }
                    cacheKey.standardQueryDocument.isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .standardQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .document(cacheKey.standardQueryDocument.orNull()!!)
                            .build()
                    }
                    cacheKey.tabularQueryOutputColumns.isDefined() -> {
                        requestMaterializationGraphContextFactory
                            .tabularQueryBuilder()
                            .materializationMetamodel(session.materializationMetamodel)
                            .variableKeys(cacheKey.variableKeys)
                            .outputColumnNames(cacheKey.tabularQueryOutputColumns.orNull()!!)
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
    ): Mono<RequestMaterializationGraph> {
        Mono.fromCallable {
            when (context) {
                is ExpectedStandardJsonInputStandardQuery -> {
                    val connector = ExpectedStandardJsonInputBasedStandardQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: ExpectedStandardJsonInputStandardQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
                    }
                }
                is ExpectedStandardJsonInputTabularQuery -> {
                    val connector = ExpectedStandardJsonInputBasedTabularQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: ExpectedStandardJsonInputTabularQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
                    }
                }
                is ExpectedTabularInputStandardQuery -> {
                    val connector = ExpectedTabularInputBasedStandardQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: ExpectedTabularInputStandardQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
                    }
                }
                is ExpectedTabularInputTabularQuery -> {
                    val connector = ExpectedTabularInputBasedTabularQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: ExpectedTabularInputTabularQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
                    }
                }
                is StandardQuery -> {
                    val connector = StandardQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: StandardQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
                    }
                }
                is TabularQuery -> {
                    val connector = TabularQueryConnector
                    connectOperationDefinitionAndEachAddedVertex(connector, context) {
                        c: TabularQuery ->
                        c.addedVertices
                            .asSequence()
                            .map(Map.Entry<GQLOperationPath, Node<*>>::toPair)
                            .firstOrNone() to c.update { dropFirstAddedVertex() }
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
        return Mono.empty()
    }

    private fun <C> connectOperationDefinitionAndEachAddedVertex(
        connector: RequestMaterializationGraphConnector<C>,
        context: C,
        pollFirstFunction: (C) -> Pair<Option<Pair<GQLOperationPath, Node<*>>>, C>,
    ): C where C : RequestMaterializationGraphContext {
        var pollResult: Pair<Option<Pair<GQLOperationPath, Node<*>>>, C> =
            pollFirstFunction.invoke(connector.connectOperationDefinition(context))
        var pOpt: Option<Pair<GQLOperationPath, Node<*>>> = pollResult.first
        var c: C = pollResult.second
        while (pOpt.isDefined()) {
            val nextVertex: Pair<GQLOperationPath, Node<*>> = pOpt.orNull()!!
            val componentContext: QueryComponentContext =
                if (nextVertex.first.argumentReferent()) {
                    nextVertex.second
                        .toOption()
                        .filterIsInstance<Argument>()
                        .map { a: Argument ->
                            queryComponentContextFactory
                                .fieldArgumentComponentContextBuilder()
                                .path(nextVertex.first)
                                .argument(a)
                                .build()
                        }
                        .getOrElse {
                            queryComponentContextFactory
                                .fieldArgumentComponentContextBuilder()
                                .path(nextVertex.first)
                                .build()
                        }
                } else {
                    nextVertex.second
                        .toOption()
                        .filterIsInstance<Field>()
                        .map { f: Field ->
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .path(nextVertex.first)
                                .field(f)
                                .build()
                        }
                        .getOrElse {
                            queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .path(nextVertex.first)
                                .build()
                        }
                }
            when (componentContext) {
                is QueryComponentContext.FieldArgumentComponentContext -> {
                    c = connector.connectFieldArgument(c, componentContext)
                }
                is QueryComponentContext.SelectedFieldComponentContext -> {
                    c = connector.connectSelectedField(c, componentContext)
                }
            }
            pollResult = pollFirstFunction.invoke(c)
            pOpt = pollResult.first
            c = pollResult.second
        }
        return c
    }
}
