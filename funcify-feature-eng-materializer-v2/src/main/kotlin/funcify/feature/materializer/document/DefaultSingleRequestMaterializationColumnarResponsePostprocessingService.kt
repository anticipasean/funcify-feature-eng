package funcify.feature.materializer.document

import arrow.core.getOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.ColumnarDocumentContext
import funcify.feature.materializer.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.json.JsonNodeSchematicPathToValueMappingExtractor
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionResult
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

/**
 * @author smccarron
 * @created 2022-10-24
 */
internal class DefaultSingleRequestMaterializationColumnarResponsePostprocessingService(
    private val jsonMapper: JsonMapper,
    private val serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory
) : SingleRequestMaterializationColumnarResponsePostprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationColumnarDocumentPreprocessingService>()
    }

    override fun postprocessColumnarExecutionResult(
        executionResult: ExecutionResult,
        columnarDocumentContext: ColumnarDocumentContext,
        session: GraphQLSingleRequestSession,
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            "postprocess_columnar_execution_result: [ columnar_document_context.expected_field_names.size: {} ]",
            columnarDocumentContext.expectedFieldNames.size
        )
        if (!executionResult.isDataPresent) {
            return Mono.fromSupplier {
                session.update {
                    serializedGraphQLResponse(
                        serializedGraphQLResponseFactory
                            .builder()
                            .executionResult(executionResult)
                            .resultAsColumnarJsonObject(JsonNodeFactory.instance.objectNode())
                            .build()
                    )
                }
            }
        }
        return Mono.defer {
                jsonMapper
                    .fromKotlinObject<Map<String, Any?>>(executionResult.getData())
                    .toJsonNode()
                    .toMono()
            }
            .flatMap { resultAsJson: JsonNode ->
                JsonNodeSchematicPathToValueMappingExtractor(resultAsJson)
                    .toOption()
                    .filter { jsonValuesByPath -> jsonValuesByPath.isNotEmpty() }
                    .successIfDefined {
                        ServiceError.of(
                            """execution_result.data as json did not 
                            |have any paths that could be extracted
                            |"""
                                .flatten()
                        )
                    }
                    .toMono()
                    .flatMap { jsonValuesByPath: ImmutableMap<GQLOperationPath, JsonNode> ->
                        Flux.fromIterable(
                                columnarDocumentContext.sourceIndexPathsByFieldName.entries
                            )
                            .flatMapSequential { (fieldName: String, path: GQLOperationPath) ->
                                ListIndexedSchematicPathGraphQLSchemaBasedCalculator(
                                        path,
                                        session.materializationSchema
                                    )
                                    .flatMap { listIndexedPath: GQLOperationPath ->
                                        jsonValuesByPath.getOrNone(listIndexedPath).map { jn ->
                                            fieldName to jn
                                        }
                                    }
                                    .toMono()
                                    .switchIfEmpty {
                                        Mono.just(fieldName to JsonNodeFactory.instance.nullNode())
                                    }
                            }
                            .reduce(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                                on.set<ObjectNode>(k, v)
                            }
                    }
            }
            .onErrorResume { t: Throwable ->
                Mono.error {
                    val message: String =
                        """unable to successfully map fields within execution_result.data 
                            |into columnar json_object format due to 
                            |[ type: %s, message: %s ]"""
                            .flatten()
                            .format(t::class.qualifiedName, t.message)
                    ServiceError.builder().message(message).cause(t).build()
                }
            }
            .map { resultAsColumnarJsonObject ->
                serializedGraphQLResponseFactory
                    .builder()
                    .executionResult(executionResult)
                    .resultAsColumnarJsonObject(resultAsColumnarJsonObject)
                    .build()
            }
            .map { response: SerializedGraphQLResponse ->
                session.update { serializedGraphQLResponse(response) }
            }
    }
}
