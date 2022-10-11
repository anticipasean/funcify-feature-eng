package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.retrieval.GraphQLQueryPathBasedComposer
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExecutionInput
import graphql.GraphQLError
import graphql.ParseAndValidate
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.Document
import graphql.validation.ValidationError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultMaterializationPreparsedDocumentProvider(private val jsonMapper: JsonMapper) :
    MaterializationPreparsedDocumentProvider {

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
                        if (entry.errors.isEmpty()) {
                            documentByRawGraphQLQueryCache[executionInput.query] = entry.document
                        }
                    }
                    .widen()
            }
            executionInput.query.isBlank() &&
                executionInput.graphQLContext
                    .getOrEmpty<List<String>>(
                        MaterializationPreparsedDocumentProvider.EXPECTED_OUTPUT_FIELD_NAMES_KEY
                    )
                    .orElseGet { emptyList<String>() }
                    .isNotEmpty() &&
                executionInput.variables.isEmpty() -> {
                createExpectedOutputFieldNamesPresentWithoutVariablesErrorPublisher(executionInput)
            }
            else -> {
                Mono.justOrEmpty(
                        executionInput.graphQLContext.get<GraphQLSingleRequestSession>(
                            GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                        )
                    )
                    .zipWith(
                        Mono.justOrEmpty(
                            executionInput.graphQLContext.get<List<String>>(
                                MaterializationPreparsedDocumentProvider
                                    .EXPECTED_OUTPUT_FIELD_NAMES_KEY
                            )
                        )
                    ) { s, e -> s to e }
                    .flatMap(
                        createPreparsedDocumentEntryForExpectedOutputFieldNamesGivenInputVariables(
                            executionInput
                        )
                    )
            }
        }
    }

    private fun createExpectedOutputFieldNamesPresentWithoutVariablesErrorPublisher(
        executionInput: ExecutionInput
    ): Mono<PreparsedDocumentEntry> {
        val expectedOutputFieldNames: List<String> =
            executionInput.graphQLContext
                .getOrEmpty<List<String>>(
                    MaterializationPreparsedDocumentProvider.EXPECTED_OUTPUT_FIELD_NAMES_KEY
                )
                .orElseGet { emptyList<String>() }
        return Mono.error(
            MaterializerException(
                MaterializerErrorResponse.INVALID_GRAPHQL_REQUEST,
                """execution_input contains 
                |expected_output_field_names [ size: %d, fields: %s ] but 
                |does not have any "variables" that can be used as arguments 
                |to fetch values for these fields 
                |e.g. a user_id to fetch user_info instances"""
                    .flatten()
                    .format(
                        expectedOutputFieldNames.size,
                        expectedOutputFieldNames.joinToString(", ")
                    )
            )
        )
    }

    private fun createPreparsedDocumentEntryForExpectedOutputFieldNamesGivenInputVariables(
        executionInput: ExecutionInput
    ): (Pair<GraphQLSingleRequestSession, List<String>>) -> Mono<PreparsedDocumentEntry> {
        return { (session, expectedOutputFieldNames) ->
            Flux.fromIterable(executionInput.variables.entries)
                .flatMap(determineMatchingParameterAttributeVertexForVariableEntry(session))
                .flatMap(convertVariableValuesIntoJsonNodeValues())
                .reduce(persistentMapOf<SchematicPath, JsonNode>()) {
                    pm,
                    (paramAttr, paramJsonValue) ->
                    pm.put(paramAttr.path, paramJsonValue)
                }
                .flatMap { parameterMap ->
                    val topLevelSrcIndexPathsSet: PersistentSet<SchematicPath> =
                        parameterMap
                            .asSequence()
                            .map { (path, _) ->
                                SchematicPath.of { pathSegments(path.pathSegments) }
                            }
                            .toPersistentSet()
                    Flux.fromIterable(expectedOutputFieldNames)
                        .flatMap(
                            determineMatchingSourceAttributeVertexForFieldNameGivenParameters(
                                topLevelSrcIndexPathsSet,
                                session
                            )
                        )
                        .reduce(persistentSetOf<SchematicPath>()) { ps, srcAttr ->
                            ps.add(srcAttr.path)
                        }
                        .map { sourceIndexPathsSet ->
                            GraphQLQueryPathBasedComposer
                                .createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
                                    sourceIndexPathsSet
                                )
                        }
                        .map { queryComposerFunction -> queryComposerFunction(parameterMap) }
                }
                .map { opDef -> Document.newDocument().definition(opDef).build() }
                .flatMap { doc ->
                    ParseAndValidate.validate(session.materializationSchema, doc)
                        .toMono()
                        .filter { validationErrors: MutableList<ValidationError> ->
                            validationErrors.isNotEmpty()
                        }
                        .map(::PreparsedDocumentEntry)
                        .switchIfEmpty { PreparsedDocumentEntry(doc).toMono() }
                }
        }
    }

    private fun determineMatchingParameterAttributeVertexForVariableEntry(
        session: GraphQLSingleRequestSession
    ): (Map.Entry<String, Any?>) -> Mono<Pair<ParameterAttributeVertex, Any?>> {
        return { (name, value) ->
            when {
                session.metamodelGraph.parameterAttributeVerticesByQualifiedName
                    .getOrNone(name)
                    .filter { paramAttrSet -> paramAttrSet.size == 1 }
                    .isDefined() -> {
                    session.metamodelGraph.parameterAttributeVerticesByQualifiedName
                        .getOrNone(name)
                        .flatMap { paramAttrSet -> paramAttrSet.firstOrNone() }
                        .map { paramAttr -> paramAttr to value }
                        .toMono()
                }
                session.metamodelGraph.attributeAliasRegistry
                    .getParameterVertexPathsWithSimilarNameOrAlias(name)
                    .toOption()
                    .filter { paramPathSet -> paramPathSet.size == 1 }
                    .isDefined() -> {
                    session.metamodelGraph.attributeAliasRegistry
                        .getParameterVertexPathsWithSimilarNameOrAlias(name)
                        .firstOrNone()
                        .flatMap { paramAttrPath ->
                            session.metamodelGraph.pathBasedGraph.getVertex(paramAttrPath)
                        }
                        .filterIsInstance<ParameterAttributeVertex>()
                        .map { paramAttr -> paramAttr to value }
                        .toMono()
                }
                else -> {
                    val message: String =
                        if (
                            session.metamodelGraph.parameterAttributeVerticesByQualifiedName
                                .getOrNone(name)
                                .filter { paramAttrSet -> paramAttrSet.size > 1 }
                                .isDefined()
                        ) {
                            """variable [ name: %s ] maps to multiple acceptable argument names; 
                                |an alias for at least one of these argument names needs 
                                |to be used in place of the configured argument name--or--
                                |the caller must use a regular GraphQL query in lieu of key-value lookup
                                |"""
                                .flatten()
                                .format(name)
                        } else {
                            """variable [ name: %s ] does not map to any known argument name 
                                |or alias for an argument"""
                                .flatten()
                                .format(name)
                        }
                    Mono.error(
                        MaterializerException(
                            MaterializerErrorResponse.INVALID_GRAPHQL_REQUEST,
                            message
                        )
                    )
                }
            }
        }
    }

    private fun convertVariableValuesIntoJsonNodeValues():
        (Pair<ParameterAttributeVertex, Any?>) -> Mono<Pair<ParameterAttributeVertex, JsonNode>> {
        return { (paramAttr, paramValue) ->
            jsonMapper
                .fromKotlinObject(paramValue)
                .toJsonNode()
                .toMono()
                .map { jn -> paramAttr to jn }
                .onErrorResume { t: Throwable ->
                    Mono.error(
                        MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """unable to serialize variable value for [ path: %s ] 
                                |into JSON due to 
                                |[ type: %s, message: %s ]"""
                                .flatten()
                                .format(paramAttr.path, t::class.qualifiedName, t.message),
                            t
                        )
                    )
                }
        }
    }

    private fun determineMatchingSourceAttributeVertexForFieldNameGivenParameters(
        topLevelSrcIndexPathsSet: PersistentSet<SchematicPath>,
        session: GraphQLSingleRequestSession
    ): (String) -> Mono<SourceAttributeVertex> {
        return { fieldName ->
            when {
                session.metamodelGraph.sourceAttributeVerticesByQualifiedName
                    .getOrNone(fieldName)
                    .filter { srcAttrSet -> srcAttrSet.size == 1 }
                    .isDefined() -> {
                    session.metamodelGraph.sourceAttributeVerticesByQualifiedName
                        .getOrNone(fieldName)
                        .flatMap { srcAttrSet -> srcAttrSet.firstOrNone() }
                        .toMono()
                }
                session.metamodelGraph.attributeAliasRegistry
                    .getSourceVertexPathWithSimilarNameOrAlias(fieldName)
                    .isDefined() -> {
                    session.metamodelGraph.attributeAliasRegistry
                        .getSourceVertexPathWithSimilarNameOrAlias(fieldName)
                        .flatMap { srcAttrPath ->
                            session.metamodelGraph.pathBasedGraph.getVertex(srcAttrPath)
                        }
                        .filterIsInstance<SourceAttributeVertex>()
                        .toMono()
                }
                session.metamodelGraph.sourceAttributeVerticesByQualifiedName
                    .getOrNone(fieldName)
                    .filter { srcAttrSet -> srcAttrSet.size > 1 }
                    .filter { srcAttrSet ->
                        srcAttrSet
                            .asSequence()
                            .filter { srcAttr ->
                                topLevelSrcIndexPathsSet.any { sp ->
                                    srcAttr.path.isDescendentOf(sp)
                                }
                            }
                            .count() == 1
                    }
                    .isDefined() -> {
                    session.metamodelGraph.sourceAttributeVerticesByQualifiedName
                        .getOrNone(fieldName)
                        .flatMap { srcAttrSet ->
                            srcAttrSet.firstOrNone { srcAttr ->
                                topLevelSrcIndexPathsSet.any { sp ->
                                    srcAttr.path.isDescendentOf(sp)
                                }
                            }
                        }
                        .toMono()
                }
                else -> {
                    val message: String =
                        if (
                            session.metamodelGraph.sourceAttributeVerticesByQualifiedName
                                .getOrNone(fieldName)
                                .filter { srcAttrSet -> srcAttrSet.size > 1 }
                                .isDefined()
                        ) {
                            """expected_output_field_name [ name: %s ] maps to multiple field_names; 
                                |an alias for at least one of these field_names needs 
                                |to be used in place of the configured field name--or--
                                |the caller must use a regular GraphQL query in lieu of key-value lookup
                                |"""
                                .flatten()
                                .format(fieldName)
                        } else {
                            """expected_output_field_name [ name: %s ] does not map to any known field_name 
                                |or alias for a field_name"""
                                .flatten()
                                .format(fieldName)
                        }
                    Mono.error(
                        MaterializerException(
                            MaterializerErrorResponse.INVALID_GRAPHQL_REQUEST,
                            message
                        )
                    )
                }
            }
        }
    }

    private fun logPreparsedDocumentEntryCreationSuccess(): (PreparsedDocumentEntry) -> Unit {
        return { preparsedDocumentEntry: PreparsedDocumentEntry ->
            val methodTag: String = "get_preparsed_document_entry"
            if (preparsedDocumentEntry.errors.isNotEmpty()) {
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
