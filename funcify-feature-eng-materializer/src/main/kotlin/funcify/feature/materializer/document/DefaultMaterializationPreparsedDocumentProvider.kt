package funcify.feature.materializer.document

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.toOption
import com.google.common.cache.CacheBuilder
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.SingleRequestMaterializationDispatchService
import funcify.feature.materializer.graph.SingleRequestMaterializationGraphService
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.AstPrinter
import graphql.language.Definition
import graphql.language.Document
import graphql.language.NamedNode
import graphql.language.OperationDefinition
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultMaterializationPreparsedDocumentProvider(
    private val jsonMapper: JsonMapper,
    private val singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
    private val singleRequestMaterializationDispatchService:
        SingleRequestMaterializationDispatchService
) : MaterializationPreparsedDocumentProvider() {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationPreparsedDocumentProvider>()
    }

    private val cache:
        ConcurrentMap<PreparsedDocumentEntryCacheKey, PreparsedDocumentEntry> by lazy {
        CacheBuilder.newBuilder()
            .expireAfterWrite(24L, TimeUnit.HOURS)
            .build<PreparsedDocumentEntryCacheKey, PreparsedDocumentEntry>()
            .asMap()
    }

    override fun getPreparsedDocumentEntry(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry,
    ): Mono<out PreparsedDocumentEntry> {
        logger.debug(
            "get_preparsed_document_entry: [ execution_input.execution_id: ${executionInput.executionId} ]"
        )
        return when {
            !executionInput.graphQLContext.hasKey(
                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
            ) -> {
                missingGraphQLSingleRequestSessionInContextErrorPublisher(executionInput)
            }
            executionInput.query.isNotBlank() -> {
                extractSessionFromGraphQLContext(executionInput)
                    .flatMap(
                        determinePreparsedDocumentEntryForSessionWithQueryText(
                            executionInput,
                            parseAndValidateFunction
                        )
                    )
            }
            else -> {
                extractSessionFromGraphQLContext(executionInput)
                    .flatMap(
                        determinePreparsedDocumentEntryForSessionWithTabularQuery(
                            executionInput,
                            parseAndValidateFunction
                        )
                    )
            }
        }.doOnNext(logPreparsedDocumentEntryCreationSuccess())
    }

    private fun <T> missingGraphQLSingleRequestSessionInContextErrorPublisher(
        executionInput: ExecutionInput
    ): Mono<T> {
        return Mono.error {
            val sessionType: String = GraphQLSingleRequestSession::class.qualifiedName ?: "<NA>"
            val contextKeys: String =
                executionInput.graphQLContext
                    .stream()
                    .map { (k: Any?, _: Any?) -> Objects.toString(k, "<NA>") }
                    .sorted()
                    .collect(Collectors.joining(", ", "{ ", " }"))
            ServiceError.of(
                """session [ type: %s ] is missing from 
                |execution_input.graphql_context: 
                |[ execution_input.graphql_context.keys: %s ] 
                |unable to do any further execution_input processing"""
                    .flatten(),
                sessionType,
                contextKeys
            )
        }
    }

    private fun extractSessionFromGraphQLContext(
        executionInput: ExecutionInput
    ): Mono<GraphQLSingleRequestSession> {
        return Mono.fromCallable {
                requireNotNull(
                    executionInput.graphQLContext.get<GraphQLSingleRequestSession>(
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                ) {
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                }
            }
            .onErrorMap(IllegalArgumentException::class.java) { iae: IllegalArgumentException ->
                ServiceError.builder()
                    .message(
                        "graphql_context contains null value for [ key: %s ]",
                        GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                    )
                    .cause(iae)
                    .build()
            }
    }

    private fun determinePreparsedDocumentEntryForSessionWithQueryText(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry
    ): (GraphQLSingleRequestSession) -> Mono<PreparsedDocumentEntry> {
        // Assumes query text should be parsed_and_validated as document before graph service called
        return { session: GraphQLSingleRequestSession ->
            val cacheKey: PreparsedDocumentEntryCacheKey =
                PreparsedDocumentEntryCacheKey(
                    materializationMetamodelCreated = session.materializationMetamodel.created,
                    rawGraphQLQueryText = session.rawGraphQLRequest.rawGraphQLQueryText
                )
            when (val pde: PreparsedDocumentEntry? = cache[cacheKey]) {
                // Case 1: Query text has not been preparsed
                null -> {
                    Mono.fromCallable {
                            requireNotNull(parseAndValidateFunction.invoke(executionInput)) {
                                "null preparsed_document_entry returned by graphql.parse_and_validate_function"
                            }
                        }
                        .onErrorMap { t: Throwable ->
                            ServiceError.builder()
                                .message("parse_and_validate_function error")
                                .cause(t)
                                .build()
                        }
                        .doOnNext { pde1: PreparsedDocumentEntry -> cache[cacheKey] = pde1 }
                        .flatMap { pde1: PreparsedDocumentEntry ->
                            when {
                                pde1.hasErrors() -> {
                                    executionInput.graphQLContext.put(
                                        GraphQLSingleRequestSession
                                            .GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                                        session.update { preparsedDocumentEntry(pde1) }
                                    )
                                    Mono.just(pde1)
                                }
                                else -> {
                                    singleRequestMaterializationGraphService
                                        .createRequestMaterializationGraphForSession(
                                            session.update { preparsedDocumentEntry(pde1) }
                                        )
                                        .flatMap { s: GraphQLSingleRequestSession ->
                                            singleRequestMaterializationDispatchService
                                                .dispatchRequestsInMaterializationGraphInSession(s)
                                        }
                                        .doOnNext { s: GraphQLSingleRequestSession ->
                                            executionInput.graphQLContext.put(
                                                GraphQLSingleRequestSession
                                                    .GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                                                s
                                            )
                                        }
                                        .then(Mono.just(pde1))
                                }
                            }
                        }
                }
                // Case 2: Query text has been preparsed
                else -> {
                    // Case 2-1: Query text had validation errors
                    when {
                        pde.hasErrors() -> {
                            executionInput.graphQLContext.put(
                                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                                session.update { preparsedDocumentEntry(pde) }
                            )
                            Mono.just(pde)
                        }
                        else -> { // Case 2-2: Query text did not have any validation errors
                            singleRequestMaterializationGraphService
                                .createRequestMaterializationGraphForSession(
                                    session.update { preparsedDocumentEntry(pde) }
                                )
                                .flatMap { s: GraphQLSingleRequestSession ->
                                    singleRequestMaterializationDispatchService
                                        .dispatchRequestsInMaterializationGraphInSession(s)
                                }
                                .doOnNext { s: GraphQLSingleRequestSession ->
                                    executionInput.graphQLContext.put(
                                        GraphQLSingleRequestSession
                                            .GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                                        s
                                    )
                                }
                                .then(Mono.just(pde))
                        }
                    }
                }
            }
        }
    }

    private fun determinePreparsedDocumentEntryForSessionWithTabularQuery(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry,
    ): (GraphQLSingleRequestSession) -> Mono<PreparsedDocumentEntry> {
        return { session: GraphQLSingleRequestSession ->
            when {
                session.rawGraphQLRequest.expectedOutputFieldNames.isEmpty() -> {
                    Mono.error<PreparsedDocumentEntry> {
                        ServiceError.invalidRequestErrorBuilder()
                            .message(
                                """session must contain either parsed GraphQL Document 
                                |i.e. a Query 
                                |or list of expected column names in the output 
                                |for a tabular query"""
                                    .flatten()
                            )
                            .build()
                    }
                }
                else -> {
                    singleRequestMaterializationGraphService
                        .createRequestMaterializationGraphForSession(session)
                        .flatMap { s: GraphQLSingleRequestSession ->
                            singleRequestMaterializationDispatchService
                                .dispatchRequestsInMaterializationGraphInSession(s)
                        }
                        .doOnNext { s: GraphQLSingleRequestSession ->
                            executionInput.graphQLContext.put(
                                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                                s
                            )
                        }
                        .flatMap { s: GraphQLSingleRequestSession ->
                            when (
                                val pde: PreparsedDocumentEntry? = s.preparsedDocumentEntry.orNull()
                            ) {
                                null -> {
                                    Mono.error<PreparsedDocumentEntry> {
                                        ServiceError.of(
                                            """session [ session_id: %s ] has not been furnished 
                                            |with a %s post graph creation and dispatch"""
                                                .flatten(),
                                            s.sessionId,
                                            PreparsedDocumentEntry::class.simpleName
                                        )
                                    }
                                }
                                else -> {
                                    Mono.just(pde)
                                }
                            }
                        }
                }
            }
        }
    }

    private fun logPreparsedDocumentEntryCreationSuccess(): (PreparsedDocumentEntry) -> Unit {
        return { preparsedDocumentEntry: PreparsedDocumentEntry ->
            val methodTag: String = "get_preparsed_document_entry"
            if (preparsedDocumentEntry.hasErrors()) {
                val documentErrorsAsStr =
                    preparsedDocumentEntry.errors
                        .toOption()
                        .fold(::emptyList, ::identity)
                        .joinToString(
                            separator = ",\n",
                            prefix = "{ ",
                            postfix = " }",
                            transform = { graphQLError: GraphQLError ->
                                Try.attempt { graphQLError.toSpecification() }
                                    .flatMap { spec ->
                                        jsonMapper.fromKotlinObject(spec).toJsonString()
                                    }
                                    .orNull() ?: "<NA>"
                            }
                        )
                logger.debug(
                    "$methodTag: [ status: failed ] [ entry.errors: $documentErrorsAsStr ]"
                )
            } else {
                val documentDefinitionsAsStr =
                    preparsedDocumentEntry.document
                        .toOption()
                        .map(Document::getDefinitions)
                        .fold(::emptyList, ::identity)
                        .asSequence()
                        .map { d: Definition<*> ->
                            d.toOption()
                                .filterIsInstance<NamedNode<*>>()
                                .map(NamedNode<*>::getName)
                                .getOrElse { "<NA>" } to (d::class.simpleName ?: "<NA>")
                        }
                        .joinToString(
                            separator = ",",
                            prefix = "{ ",
                            postfix = " }",
                            transform = { (n: String, t: String) -> "[ name: $n, type: $t ]" }
                        )
                logger.debug(
                    "$methodTag: [ status: success ] [ entry.definitions: $documentDefinitionsAsStr ]"
                )
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "operation_definition: \n{}",
                        preparsedDocumentEntry.document.definitions
                            .asSequence()
                            .filterIsInstance<OperationDefinition>()
                            .firstOrNone()
                            .map(AstPrinter::printAst)
                            .getOrElse { "<NA>" }
                    )
                }
            }
        }
    }
}
