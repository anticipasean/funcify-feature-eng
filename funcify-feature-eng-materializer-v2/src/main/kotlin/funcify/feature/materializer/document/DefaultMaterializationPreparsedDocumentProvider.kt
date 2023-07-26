package funcify.feature.materializer.document

import arrow.core.identity
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.stream.Collectors
import org.slf4j.Logger
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultMaterializationPreparsedDocumentProvider(
    private val jsonMapper: JsonMapper,
    private val singleRequestMaterializationColumnarDocumentPreprocessingService:
        SingleRequestMaterializationColumnarDocumentPreprocessingService
) : MaterializationPreparsedDocumentProvider() {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationPreparsedDocumentProvider>()
    }

    private val documentByRawGraphQLQueryCache: ConcurrentMap<String, Document> =
        ConcurrentHashMap()

    override fun getPreparsedDocumentEntry(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry,
    ): Mono<PreparsedDocumentEntry> {
        logger.debug(
            "get_preparsed_document_entry: [ execution_input.execution_id: ${executionInput.executionId} ]"
        )
        return when {
            executionInput.query.isNotBlank() &&
                executionInput.query in documentByRawGraphQLQueryCache -> {
                documentByRawGraphQLQueryCache[executionInput.query].toMono().map {
                    document: Document ->
                    PreparsedDocumentEntry(document)
                }
            }
            executionInput.query.isNotBlank() -> {
                Try.attempt { parseAndValidateFunction.invoke(executionInput) }
                    .peekIfSuccess(logPreparsedDocumentEntryCreationSuccess())
                    .toMono()
                    .doOnNext { entry: PreparsedDocumentEntry ->
                        if (!entry.hasErrors()) {
                            documentByRawGraphQLQueryCache[executionInput.query] = entry.document
                        }
                    }
                    .widen()
            }
            executionInput.query.isBlank() &&
                !executionInput.graphQLContext.hasKey(
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                ) -> {
                missingGraphQLSingleRequestSessionInContextErrorPublisher(executionInput)
            }
            else -> {
                Mono.fromCallable {
                        executionInput.graphQLContext.get<GraphQLSingleRequestSession>(
                            GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                        )!!
                    }
                    .onErrorMap(NullPointerException::class.java) { npe: NullPointerException ->
                        ServiceError.builder()
                            .message(
                                "graphql_context contains null value for [ key: %s ]",
                                GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                            )
                            .cause(npe)
                            .build()
                    }
                    .flatMap { session: GraphQLSingleRequestSession ->
                        singleRequestMaterializationColumnarDocumentPreprocessingService
                            .preprocessColumnarDocumentForExecutionInput(executionInput, session)
                    }
                    .cache()
            }
        }
    }

    private fun <T> missingGraphQLSingleRequestSessionInContextErrorPublisher(
        executionInput: ExecutionInput
    ): Mono<T> {
        return Mono.error {
            val sessionType: String = GraphQLSingleRequestSession::class.qualifiedName ?: "<NA>"
            val contextKeys: String =
                executionInput.graphQLContext
                    .stream()
                    .map { (k, _) -> Objects.toString(k, "<NA>") }
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
                                    .orNull()
                                    ?: "<NA>"
                            }
                        )
                logger.debug(
                    "$methodTag: [ status: failed ] [ entry.errors: $documentErrorsAsStr ]"
                )
            } else {
                val documentDefinitionsAsStr =
                    preparsedDocumentEntry.document
                        .toOption()
                        .map { doc -> doc.definitions }
                        .fold(::emptyList, ::identity)
                        .joinToString(
                            separator = ",\n",
                            prefix = "{ ",
                            postfix = " }",
                            transform = { def -> def.toString() }
                        )
                logger.debug(
                    "$methodTag: [ status: success ] [ entry.definitions: $documentDefinitionsAsStr ]"
                )
            }
        }
    }
}
