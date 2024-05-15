package funcify.feature.materializer.response

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.response.factory.SerializedGraphQLResponseFactory
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import funcify.feature.schema.json.JsonNodeValueExtractionByOperationPath
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.tryFold
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionResult
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-10-24
 */
internal class DefaultSingleRequestMaterializationTabularResponsePostprocessingService(
    private val jsonMapper: JsonMapper,
    private val serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory
) : SingleRequestMaterializationTabularResponsePostprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationTabularResponsePostprocessingService>()

        private operator fun ObjectNode.plus(pair: Pair<String, JsonNode>): ObjectNode {
            return this.set(pair.first, pair.second)
        }
    }

    override fun postprocessTabularExecutionResult(
        executionResult: ExecutionResult,
        tabularDocumentContext: TabularDocumentContext,
        session: GraphQLSingleRequestSession,
    ): Mono<out GraphQLSingleRequestSession> {
        logger.info(
            "postprocess_tabular_execution_result: [ session.raw_graphql_request.expected_output_field_names.size: {} ]",
            session.rawGraphQLRequest.expectedOutputFieldNames.size
        )
        return if (!executionResult.isDataPresent) {
            Mono.fromSupplier {
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
        } else {
            convertExecutionResultIntoDataJson(executionResult)
                .flatMap { dataJson: JsonNode ->
                    extractEachExpectedOutputFieldNameValueFromDataJson(
                        session,
                        tabularDocumentContext,
                        dataJson
                    )
                }
                .map { tabularObjectNodeResult: JsonNode ->
                    session.update {
                        serializedGraphQLResponse(
                            serializedGraphQLResponseFactory
                                .builder()
                                .executionResult(executionResult)
                                .resultAsColumnarJsonObject(tabularObjectNodeResult)
                                .build()
                        )
                    }
                }
                .toMono()
        }
    }

    private fun convertExecutionResultIntoDataJson(
        executionResult: ExecutionResult
    ): Try<JsonNode> {
        return jsonMapper
            .fromKotlinObject(executionResult.getData())
            .toJsonNode()
            .mapFailure { t: Throwable ->
                ServiceError.builder()
                    .message(
                        "unable to create instance of [ type: %s ] from execution_result.data",
                        JsonNode::class.qualifiedName
                    )
                    .cause(t)
                    .build()
            }
            .peek({ dataJson: JsonNode ->
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "convert_execution_result_into_data_json: [ status: successful ][ data_json: {} ]",
                        jsonMapper.fromJsonNode(dataJson).toJsonString().orElse("")
                    )
                }
            }) { t: Throwable ->
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "convert_execution_result_into_data_json: [ status: failed ][ error: {} ]",
                        (t as ServiceError).toJsonNode()
                    )
                }
            }
    }

    private fun extractEachExpectedOutputFieldNameValueFromDataJson(
        session: GraphQLSingleRequestSession,
        tabularDocumentContext: TabularDocumentContext,
        dataJson: JsonNode,
    ): Try<ObjectNode> {
        return session.rawGraphQLRequest.expectedOutputFieldNames
            .asSequence()
            .map { columnName: String ->
                mapExpectedOutputFieldNameToJsonValueInDataJsonOrRawInputContext(
                    columnName,
                    tabularDocumentContext,
                    dataJson,
                    session
                )
            }
            .tryFold(JsonNodeFactory.instance.objectNode()) { on, p -> on + p }
    }

    private fun mapExpectedOutputFieldNameToJsonValueInDataJsonOrRawInputContext(
        columnName: String,
        tabularDocumentContext: TabularDocumentContext,
        dataJson: JsonNode,
        session: GraphQLSingleRequestSession,
    ): Try<Pair<String, JsonNode>> {
        return when (columnName) {
            in tabularDocumentContext.featurePathByExpectedOutputFieldName -> {
                JsonNodeValueExtractionByOperationPath.invoke(
                        dataJson,
                        tabularDocumentContext.featurePathByExpectedOutputFieldName[columnName]!!
                    )
                    .map { jn: JsonNode -> columnName to jn }
                    .successIfDefined(
                        entryNotFoundForPathInExecutionResultDataJsonError(
                            columnName,
                            tabularDocumentContext.featurePathByExpectedOutputFieldName[
                                    columnName]!!
                        )
                    )
            }
            in tabularDocumentContext.dataElementPathsByExpectedOutputFieldName -> {
                JsonNodeValueExtractionByOperationPath.invoke(
                        dataJson,
                        tabularDocumentContext.dataElementPathsByExpectedOutputFieldName[
                                columnName]!!
                    )
                    .map { jn: JsonNode -> columnName to jn }
                    .successIfDefined(
                        entryNotFoundForPathInExecutionResultDataJsonError(
                            columnName,
                            tabularDocumentContext.dataElementPathsByExpectedOutputFieldName[
                                    columnName]!!
                        )
                    )
            }
            in tabularDocumentContext.passThruExpectedOutputFieldNames -> {
                session.rawInputContext
                    .flatMap { ric: RawInputContext ->
                        ric.get(columnName).map { jn: JsonNode -> columnName to jn }
                    }
                    .successIfDefined {
                        ServiceError.of(
                            "raw_input_context does not contain entry for [ expected_output_field_name: %s ]",
                            columnName
                        )
                    }
            }
            else -> {
                Try.failure(
                    ServiceError.of(
                        "tabular_document_context does not contain mapping for [ expected_output_field_name: %s ]",
                        columnName
                    )
                )
            }
        }
    }

    private fun entryNotFoundForPathInExecutionResultDataJsonError(
        columnName: String,
        path: GQLOperationPath
    ): () -> ServiceError {
        return {
            ServiceError.of(
                "execution_result.data does not contain entry for [ expected_output_field_name: %s, path: %s ]",
                columnName,
                path
            )
        }
    }
}
