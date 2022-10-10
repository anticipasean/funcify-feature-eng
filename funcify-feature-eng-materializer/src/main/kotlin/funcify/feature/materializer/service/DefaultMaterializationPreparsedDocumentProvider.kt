package funcify.feature.materializer.service

import arrow.core.identity
import arrow.core.toOption
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.execution.preparsed.PreparsedDocumentEntry
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultMaterializationPreparsedDocumentProvider(
    private val jsonMapper: JsonMapper,
    private val materializationMetamodelBroker: MaterializationMetamodelBroker
) : MaterializationPreparsedDocumentProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationPreparsedDocumentProvider>()
    }

    override fun getPreparsedDocument(
        executionInput: ExecutionInput,
        parseAndValidateFunction: (ExecutionInput) -> PreparsedDocumentEntry,
    ): Mono<PreparsedDocumentEntry> {
        logger.debug(
            "get_preparsed_document: [ execution_input.execution_id: ${executionInput.executionId} ]"
        )
        return Try.attempt { parseAndValidateFunction.invoke(executionInput) }
            .peekIfSuccess { preparsedDocumentEntry: PreparsedDocumentEntry ->
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
                logger.debug("preparsed_document_entry: [ entry.errors: $documentErrorsAsStr ]")
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
                    "preparsed_document_entry: [ entry.definitions: $documentDefinitionsAsStr ]"
                )
            }
            .toMono()
            .widen()
    }
}
