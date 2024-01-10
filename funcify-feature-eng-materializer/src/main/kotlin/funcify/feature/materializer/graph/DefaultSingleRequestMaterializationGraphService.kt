package funcify.feature.materializer.graph

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import com.google.common.cache.CacheBuilder
import funcify.feature.error.ServiceError
import funcify.feature.graph.PersistentGraphFactory
import funcify.feature.graph.line.Line
import funcify.feature.materializer.graph.component.DefaultQueryComponentContextFactory
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.graph.connector.LazyStandardQueryTraverser
import funcify.feature.materializer.graph.connector.StandardQueryConnector
import funcify.feature.materializer.graph.connector.TabularQueryDocumentCreator
import funcify.feature.materializer.graph.context.DefaultRequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.document.GQLDocumentComposer
import funcify.feature.schema.document.GQLDocumentSpecFactory
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import funcify.feature.tools.extensions.TryExtensions.tryFold
import graphql.GraphQLError
import graphql.ParseAndValidate
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.validation.ValidationError
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
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
        DefaultQueryComponentContextFactory,
    private val tabularQueryDocumentCreator: TabularQueryDocumentCreator =
        TabularQueryDocumentCreator(
            documentSpecFactory = GQLDocumentSpecFactory.defaultFactory(),
            gqlDocumentComposer = GQLDocumentComposer.defaultComposer(),
        )
) : SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationGraphService>()
        private const val METHOD_TAG: String = "create_request_materialization_graph_for_session"
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
        logger.debug("$METHOD_TAG: [ session.session_id: ${session.sessionId} ]")
        return when {
                session.requestMaterializationGraph.isDefined() -> {
                    Mono.just(session)
                }
                else -> {
                    Mono.defer { createRequestMaterializationGraphCacheKeyForSession(session) }
                        .flatMap { rmgck: RequestMaterializationGraphCacheKey ->
                            when (
                                val existingGraph: RequestMaterializationGraph? =
                                    requestMaterializationGraphCache[rmgck]
                            ) {
                                null -> {
                                    calculateRequestMaterializationGraphForSession(rmgck, session)
                                        .doOnNext { calculatedGraph: RequestMaterializationGraph ->
                                            requestMaterializationGraphCache[rmgck] =
                                                calculatedGraph
                                        }
                                        .map { calculatedGraph: RequestMaterializationGraph ->
                                            when (
                                                val e: ServiceError? =
                                                    calculatedGraph.processingError.orNull()
                                            ) {
                                                null -> {
                                                    session.update {
                                                        preparsedDocumentEntry(
                                                            calculatedGraph.preparsedDocumentEntry
                                                        )
                                                        requestMaterializationGraph(calculatedGraph)
                                                    }
                                                }
                                                else -> {
                                                    throw e
                                                }
                                            }
                                        }
                                }
                                else -> {
                                    Mono.fromCallable {
                                        when (
                                            val e: ServiceError? =
                                                existingGraph.processingError.orNull()
                                        ) {
                                            null -> {
                                                session.update {
                                                    preparsedDocumentEntry(
                                                        existingGraph.preparsedDocumentEntry
                                                    )
                                                    requestMaterializationGraph(existingGraph)
                                                }
                                            }
                                            else -> {
                                                throw e
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }
            .doOnNext(requestMaterializationGraphSuccessLogger())
            .doOnError(requestMaterializationGraphFailureLogger())
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
                        materializationMetamodelCreated = session.materializationMetamodel.created,
                        variableKeys = session.rawGraphQLRequest.variables.keys.toPersistentSet(),
                        rawInputContextKeys =
                            session.rawInputContext.fold(
                                ::persistentSetOf,
                                RawInputContext::fieldNames
                            ),
                        operationName = session.rawGraphQLRequest.operationName.toOption(),
                        preparsedDocumentEntry = session.preparsedDocumentEntry,
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
                            .joinToString(", ") { (idx: Int, ge: GraphQLError) -> "[$idx]: $ge" }
                    )
                    .build()
                    .failure()
            }
            session.rawGraphQLRequest.expectedOutputFieldNames.isNotEmpty() -> {
                RequestMaterializationGraphCacheKey(
                        materializationMetamodelCreated = session.materializationMetamodel.created,
                        variableKeys = session.rawGraphQLRequest.variables.keys.toPersistentSet(),
                        rawInputContextKeys =
                            session.rawInputContext.fold(
                                ::persistentSetOf,
                                RawInputContext::fieldNames
                            ),
                        operationName = none(),
                        preparsedDocumentEntry = none(),
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
        }.toMono()
    }

    private fun calculateRequestMaterializationGraphForSession(
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession
    ): Mono<out RequestMaterializationGraph> {
        logger.info(
            "calculate_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        return Mono.fromCallable {
                createRequestMaterializationGraphContextForCacheKey(cacheKey, session)
            }
            .flatMap { c: RequestMaterializationGraphContext ->
                createAndValidateGQLDocumentForTabularQueryIfApt(c)
            }
            .flatMap { c: RequestMaterializationGraphContext ->
                traverseNodesWithinContextCreatingGraphComponents(c)
            }
            .flatMap { c: RequestMaterializationGraphContext ->
                createRequestMaterializationGraphFromContext(c)
            }
            .onErrorResume { t: Throwable ->
                convertErrorIntoRequestMaterializationGraphInstance(t)
            }
    }

    private fun createRequestMaterializationGraphContextForCacheKey(
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession,
    ): RequestMaterializationGraphContext {
        return when {
            cacheKey.preparsedDocumentEntry
                .filterNot(PreparsedDocumentEntry::hasErrors)
                .mapNotNull(PreparsedDocumentEntry::getDocument)
                .isDefined() -> {
                requestMaterializationGraphContextFactory
                    .standardQueryBuilder()
                    .materializationMetamodel(session.materializationMetamodel)
                    .queryComponentContextFactory(queryComponentContextFactory)
                    .variableKeys(cacheKey.variableKeys)
                    .rawInputContextKeys(cacheKey.rawInputContextKeys)
                    .operationName(cacheKey.operationName.orNull()!!)
                    .document(
                        cacheKey.preparsedDocumentEntry
                            .mapNotNull(PreparsedDocumentEntry::getDocument)
                            .orNull()!!
                    )
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

    private fun createAndValidateGQLDocumentForTabularQueryIfApt(
        context: RequestMaterializationGraphContext
    ): Mono<out RequestMaterializationGraphContext> {
        return when (context) {
            is StandardQuery -> {
                Mono.just(context)
            }
            is TabularQuery -> {
                Mono.fromCallable {
                        tabularQueryDocumentCreator.createDocumentForTabularQuery(context)
                    }
                    .doOnNext { d: Document ->
                        logger.debug(
                            "tabular_query_generated_document: \n{}",
                            AstPrinter.printAst(d)
                        )
                    }
                    .map { d: Document ->
                        val ves: List<ValidationError>? =
                            ParseAndValidate.validate(
                                context.materializationMetamodel.materializationGraphQLSchema,
                                d,
                                graphQLDocumentValidationRulesToApplyCondition(),
                                // TODO: Determine whether locale should be sourced from
                                // raw_graphql_request
                                Locale.getDefault()
                            )
                        when {
                            ves?.isNotEmpty() == true -> {
                                PreparsedDocumentEntry(d, ves)
                            }
                            else -> {
                                PreparsedDocumentEntry(d)
                            }
                        }
                    }
                    .flatMap { pde: PreparsedDocumentEntry ->
                        when {
                            pde.hasErrors() -> {
                                Mono.error {
                                    ServiceError.of(
                                        """unable to create valid %s that fits tabular_query 
                                        |[ validation_errors.size: %s ]
                                        |[ %s ]"""
                                            .flatten(),
                                        Document::class.qualifiedName,
                                        pde.errors.size,
                                        pde.errors.asSequence().withIndex().joinToString(",") {
                                            (idx: Int, ge: GraphQLError) ->
                                            "[${idx}]: ${ge}"
                                        }
                                    )
                                }
                            }
                            else -> {
                                Mono.fromCallable {
                                    requestMaterializationGraphContextFactory
                                        .standardQueryBuilder()
                                        .materializationMetamodel(context.materializationMetamodel)
                                        .queryComponentContextFactory(queryComponentContextFactory)
                                        .variableKeys(context.variableKeys)
                                        .rawInputContextKeys(context.rawInputContextKeys)
                                        .operationName("")
                                        .document(pde.document)
                                        .requestGraph(context.requestGraph)
                                        .build()
                                }
                            }
                        }
                    }
            }
            else -> {
                Mono.error {
                    ServiceError.of(
                        "unsupported request_materialization_graph_context.type: [ type: %s ]",
                        context::class.qualifiedName
                    )
                }
            }
        }
    }

    /** Specifies which graphql.validation.rules.* member to have applied */
    private fun graphQLDocumentValidationRulesToApplyCondition(): (Class<*>) -> Boolean {
        return { _: Class<*> -> true }
    }

    private fun traverseNodesWithinContextCreatingGraphComponents(
        context: RequestMaterializationGraphContext
    ): Mono<out RequestMaterializationGraphContext> {
        return Mono.fromCallable {
                when (context) {
                    is StandardQuery -> {
                        val connector = StandardQueryConnector
                        LazyStandardQueryTraverser.invoke(context).fold(context) {
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
                    else -> {
                        throw ServiceError.of(
                            "unsupported request_materialization_graph_context.type: [ type: %s ]",
                            context::class.qualifiedName
                        )
                    }
                }
            }
            .doOnNext { rmgc: RequestMaterializationGraphContext ->
                val vertexStringifier: (QueryComponentContext?) -> String =
                    { qcc: QueryComponentContext? ->
                        qcc?.run {
                                this::class
                                    .supertypes
                                    .asSequence()
                                    .mapNotNull(KType::classifier)
                                    .filterIsInstance<KClass<*>>()
                                    .filter(QueryComponentContext::class::isSuperclassOf)
                                    .mapNotNull(KClass<*>::simpleName)
                                    .firstOrNull()
                            }
                            .orEmpty()
                    }
                logger.debug(
                    "generated_graph: \n{}",
                    buildString {
                        append("vertices: [\n")
                        rmgc.requestGraph.foldLeftVertices(this) {
                            sb: StringBuilder,
                            (p: GQLOperationPath, v: QueryComponentContext) ->
                            sb.append("  ")
                                .append("[")
                                .append(p)
                                .append("]: ")
                                .append(vertexStringifier(v))
                                .append("\n")
                        }
                        append("]\n")
                        append("edges: [\n")
                        rmgc.requestGraph.foldLeftEdges(this) {
                            sb: StringBuilder,
                            (l: Line<GQLOperationPath>, e: MaterializationEdge) ->
                            sb.append("  ")
                                .append("[")
                                .append(l.component1())
                                .append("]")
                                .append(": ")
                                .append(vertexStringifier(rmgc.requestGraph[l.component1()]))
                                .append("  --> ")
                                .append("[")
                                .append(e)
                                .append("]")
                                .append("  -->  ")
                                .append("[")
                                .append(l.component2())
                                .append("]")
                                .append(": ")
                                .append(vertexStringifier(rmgc.requestGraph[l.component2()]))
                                .append("\n")
                        }
                        append("]\n")
                    }
                )
            }
    }

    private fun createRequestMaterializationGraphFromContext(
        context: RequestMaterializationGraphContext
    ): Mono<out RequestMaterializationGraph> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "create_request_materialization_graph_from_context: [ context_type: {} ]",
                context::class
                    .supertypes
                    .asSequence()
                    .mapNotNull(KType::classifier)
                    .filterIsInstance<KClass<*>>()
                    .filter(RequestMaterializationGraphContext::class::isSuperclassOf)
                    .mapNotNull(KClass<*>::simpleName)
                    .firstOrNull()
            )
        }
        return Mono.fromCallable {
            when (context) {
                is StandardQuery -> {
                    DefaultRequestMaterializationGraph(
                        operationName = context.operationName.toOption().filter(String::isNotBlank),
                        preparsedDocumentEntry = PreparsedDocumentEntry(context.document),
                        requestGraph = context.requestGraph,
                        passThruColumns = context.passThroughColumns,
                        transformerCallablesByPath = context.transformerCallablesByPath,
                        dataElementCallablesByPath =
                            context.dataElementCallableBuildersByPath
                                .asSequence()
                                .map { (p: GQLOperationPath, decb: DataElementCallable.Builder) ->
                                    Try.attempt { p to decb.build() }
                                }
                                .tryFold(
                                    persistentMapOf<GQLOperationPath, DataElementCallable>(),
                                    PersistentMap<GQLOperationPath, DataElementCallable>::plus
                                )
                                .orElseThrow(),
                        featureCalculatorCallablesByPath = context.featureCalculatorCallablesByPath,
                        featureJsonValueStoreByPath = context.featureJsonValueStoresByPath,
                        featureJsonValuePublisherByPath = context.featureJsonValuePublishersByPath,
                        lastUpdatedDataElementPathsByDataElementPath =
                            context.lastUpdatedDataElementPathsByDataElementPath,
                        processingError = none()
                    )
                }
                else -> {
                    throw ServiceError.of(
                        "unhandled context sub_type [ %s ]; cannot convert to %s",
                        context::class.simpleName,
                        RequestMaterializationGraph::class.simpleName
                    )
                }
            }
        }
    }

    private fun convertErrorIntoRequestMaterializationGraphInstance(
        throwable: Throwable
    ): Mono<out RequestMaterializationGraph> {
        return when (throwable) {
            is ServiceError -> {
                throwable
            }
            else -> {
                ServiceError.builder()
                    .message("request_materialization_graph error")
                    .cause(throwable)
                    .build()
            }
        }.let { se: ServiceError ->
            Mono.just(
                DefaultRequestMaterializationGraph(
                    operationName = none(),
                    preparsedDocumentEntry = PreparsedDocumentEntry(listOf()),
                    requestGraph =
                        PersistentGraphFactory.defaultFactory().builder().directed().build(),
                    passThruColumns = persistentSetOf(),
                    transformerCallablesByPath = persistentMapOf(),
                    dataElementCallablesByPath = persistentMapOf(),
                    featureJsonValueStoreByPath = persistentMapOf(),
                    featureCalculatorCallablesByPath = persistentMapOf(),
                    featureJsonValuePublisherByPath = persistentMapOf(),
                    lastUpdatedDataElementPathsByDataElementPath = persistentMapOf(),
                    processingError = se.some()
                )
            )
        }
    }

    private fun requestMaterializationGraphSuccessLogger(): (GraphQLSingleRequestSession) -> Unit {
        return { _: GraphQLSingleRequestSession ->
            logger.info("${METHOD_TAG}: [ status: successful ]")
        }
    }

    private fun requestMaterializationGraphFailureLogger(): (Throwable) -> Unit {
        return { t: Throwable ->
            logger.info(
                "${METHOD_TAG}: [ status: failed ][ type: {}, message: {} ]",
                t.toOption()
                    .filterIsInstance<ServiceError>()
                    .and(ServiceError::class.simpleName.toOption())
                    .orElse { t::class.simpleName.toOption() }
                    .getOrElse { "<NA>" },
                t.message
            )
        }
    }
}
