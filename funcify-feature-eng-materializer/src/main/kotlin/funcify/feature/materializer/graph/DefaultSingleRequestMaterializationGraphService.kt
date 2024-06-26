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
import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.graph.component.DefaultQueryComponentContextFactory
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.graph.connector.LazyStandardQueryTraverser
import funcify.feature.materializer.graph.connector.RequestMaterializationGraphContextPreconnector
import funcify.feature.materializer.graph.connector.StandardQueryConnector
import funcify.feature.materializer.graph.connector.TabularQueryDocumentCreator
import funcify.feature.materializer.graph.context.DefaultRequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf

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
        TabularQueryDocumentCreator()
) : SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationGraphService>()
        private const val METHOD_TAG: String = "create_request_materialization_graph_for_session"
    }

    private val requestMaterializationGraphCache:
        ConcurrentMap<RequestMaterializationGraphCacheKey, RequestMaterializationGraph> by lazy {
        CacheBuilder.newBuilder()
            .expireAfterWrite(24L, TimeUnit.HOURS)
            .build<RequestMaterializationGraphCacheKey, RequestMaterializationGraph>()
            .asMap()
    }

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession> {
        logger.debug("{}: [ session.session_id: {} ]", METHOD_TAG, session.sessionId)
        return when {
                session.requestMaterializationGraph.isDefined() -> {
                    Mono.just(session)
                }
                else -> {
                    createGraphCacheKeyForSession(session).flatMap {
                        rmgck: RequestMaterializationGraphCacheKey ->
                        useExistingElseCreateGraphForCacheKey(rmgck, session)
                    }
                }
            }
            .doOnNext(requestMaterializationGraphSuccessLogger())
            .doOnError(requestMaterializationGraphFailureLogger())
    }

    private fun createGraphCacheKeyForSession(
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

    private fun useExistingElseCreateGraphForCacheKey(
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession,
    ): Mono<out GraphQLSingleRequestSession> {
        logger.info(
            "use_existing_else_create_graph_for_cache_key: [ cache_key.hash_code: {} ]",
            cacheKey.hashCode()
        )
        return when (
            val existingGraph: RequestMaterializationGraph? =
                requestMaterializationGraphCache[cacheKey]
        ) {
            null -> {
                calculateRequestMaterializationGraphForSession(cacheKey, session)
                    .doOnNext { calculatedGraph: RequestMaterializationGraph ->
                        requestMaterializationGraphCache[cacheKey] = calculatedGraph
                    }
                    .map { calculatedGraph: RequestMaterializationGraph ->
                        when (val e: ServiceError? = calculatedGraph.processingError.orNull()) {
                            null -> {
                                session.update {
                                    preparsedDocumentEntry(calculatedGraph.preparsedDocumentEntry)
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
                    when (val e: ServiceError? = existingGraph.processingError.orNull()) {
                        null -> {
                            session.update {
                                preparsedDocumentEntry(existingGraph.preparsedDocumentEntry)
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

    private fun calculateRequestMaterializationGraphForSession(
        cacheKey: RequestMaterializationGraphCacheKey,
        session: GraphQLSingleRequestSession
    ): Mono<out RequestMaterializationGraph> {
        logger.info(
            "calculate_request_materialization_graph_for_session: [ session.session_id: {} ]",
            session.sessionId
        )
        return Mono.fromCallable {
                createRequestMaterializationGraphContextForCacheKey(cacheKey, session)
            }
            .flatMap { c: RequestMaterializationGraphContext ->
                preprocessContextBeforeConnectingGraph(c)
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
                    .gqlDocumentSpecFactory(GQLDocumentSpecFactory.defaultFactory())
                    .gqlDocumentComposer(GQLDocumentComposer.defaultComposer())
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
                    .gqlDocumentSpecFactory(GQLDocumentSpecFactory.defaultFactory())
                    .gqlDocumentComposer(GQLDocumentComposer.defaultComposer())
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

    private fun preprocessContextBeforeConnectingGraph(
        context: RequestMaterializationGraphContext
    ): Mono<out RequestMaterializationGraphContext> {
        return Mono.fromCallable { RequestMaterializationGraphContextPreconnector.invoke(context) }
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
                        tabularQueryDocumentCreator.createDocumentContextForTabularQuery(context)
                    }
                    .doOnNext { tdc: TabularDocumentContext ->
                        logger.debug(
                            "tabular_query_generated_document: \n{}",
                            AstPrinter.printAst(tdc.document.orNull())
                        )
                    }
                    .map { tdc: TabularDocumentContext ->
                        val ves: List<ValidationError>? =
                            ParseAndValidate.validate(
                                context.materializationMetamodel.materializationGraphQLSchema,
                                tdc.document.orNull()!!,
                                graphQLDocumentValidationRulesToApplyCondition(),
                                // TODO: Determine whether locale should be sourced from
                                // raw_graphql_request
                                Locale.getDefault()
                            )
                        when {
                            ves?.isNotEmpty() == true -> {
                                tdc to PreparsedDocumentEntry(tdc.document.orNull()!!, ves)
                            }
                            else -> {
                                tdc to PreparsedDocumentEntry(tdc.document.orNull()!!)
                            }
                        }
                    }
                    .flatMap { (tdc: TabularDocumentContext, pde: PreparsedDocumentEntry) ->
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
                                        pde.errors.asSequence().withIndex().joinToString(", ") {
                                            (idx: Int, ge: GraphQLError) ->
                                            val altStringRep: () -> String = {
                                                mapOf(
                                                        "type" to ge::class.qualifiedName,
                                                        "errorType" to ge.errorType,
                                                        "path" to ge.path,
                                                        "message" to ge.message
                                                    )
                                                    .asSequence()
                                                    .joinToString(", ", "{ ", " }") { (k, v) ->
                                                        StringBuilder(k)
                                                            .append(": ")
                                                            .append(v)
                                                            .toString()
                                                    }
                                            }
                                            "[${idx}]: ${Try.attempt(ge::toString).orElseGet(altStringRep)}"
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
                                        .gqlDocumentSpecFactory(context.gqlDocumentSpecFactory)
                                        .gqlDocumentComposer(context.gqlDocumentComposer)
                                        .variableKeys(context.variableKeys)
                                        .rawInputContextKeys(context.rawInputContextKeys)
                                        .operationName("")
                                        .document(pde.document)
                                        .requestGraph(context.requestGraph)
                                        .tabularDocumentContext(tdc)
                                        .setMatchingVariablesForArgumentsForDomainDataElementPath(
                                            context
                                                .matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                                                .toPersistentMap()
                                        )
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
                        val connector: GraphQLQueryGraphConnector<StandardQuery> =
                            StandardQueryConnector
                        LazyStandardQueryTraverser.invoke(context).fold(context) {
                            sq: StandardQuery,
                            qcc: QueryComponentContext ->
                            when (qcc) {
                                is QueryComponentContext.ArgumentComponentContext -> {
                                    connector.connectArgument(sq, qcc)
                                }
                                is QueryComponentContext.FieldComponentContext -> {
                                    connector.connectField(sq, qcc)
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
                        tabularDocumentContext = context.tabularDocumentContext,
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
                        PersistentGraphFactory.defaultFactory()
                            .builder()
                            .directed()
                            .permitParallelEdges()
                            .build(),
                    passThruColumns = persistentSetOf(),
                    transformerCallablesByPath = persistentMapOf(),
                    dataElementCallablesByPath = persistentMapOf(),
                    featureJsonValueStoreByPath = persistentMapOf(),
                    featureCalculatorCallablesByPath = persistentMapOf(),
                    featureJsonValuePublisherByPath = persistentMapOf(),
                    lastUpdatedDataElementPathsByDataElementPath = persistentMapOf(),
                    tabularDocumentContext = none(),
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
