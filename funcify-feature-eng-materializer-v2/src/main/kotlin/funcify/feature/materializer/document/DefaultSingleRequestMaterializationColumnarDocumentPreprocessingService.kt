package funcify.feature.materializer.document

import arrow.core.firstOrNone
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.retrieval.GraphQLQueryPathBasedComposer
import funcify.feature.error.ServiceError
import funcify.feature.materializer.context.document.ColumnarDocumentContext
import funcify.feature.materializer.context.document.ColumnarDocumentContextFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.PersistentSetExtensions.reduceToPersistentSet
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionInput
import graphql.ParseAndValidate
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.validation.ValidationError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono

/**
 * @author smccarron
 * @created 2022-10-24
 */
internal class DefaultSingleRequestMaterializationColumnarDocumentPreprocessingService(
    private val jsonMapper: JsonMapper,
    private val columnarDocumentContextFactory: ColumnarDocumentContextFactory
) : SingleRequestMaterializationColumnarDocumentPreprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationColumnarDocumentPreprocessingService>()
    }

    override fun preprocessColumnarDocumentForExecutionInput(
        executionInput: ExecutionInput,
        session: GraphQLSingleRequestSession
    ): Mono<PreparsedDocumentEntry> {
        val methodTag: String = "preprocess_columnar_document_for_execution_input"
        logger.info(
            "{}: [ session.raw_graphql_request.expected_output_field_names.size: {} ]",
            methodTag,
            session.rawGraphQLRequest.expectedOutputFieldNames.size
        )
        return when {
            executionInput.query.isBlank() &&
                session.rawGraphQLRequest.expectedOutputFieldNames.isEmpty() -> {
                createExpectedOutputFieldNamesEmptyErrorPublisher()
            }
            executionInput.query.isBlank() &&
                session.rawGraphQLRequest.expectedOutputFieldNames.isNotEmpty() &&
                session.rawGraphQLRequest.variables.isEmpty() -> {
                createExpectedOutputFieldNamesPresentWithoutVariablesErrorPublisher(session)
            }
            else -> {
                createPreparsedDocumentEntryForExpectedOutputFieldNamesGivenInputVariables(
                    executionInput,
                    session
                )
            }
        }
    }

    private fun <T> createExpectedOutputFieldNamesEmptyErrorPublisher(): Mono<T> {
        return Mono.error {
            ServiceError.of(
                """session neither contains graphql_query for 
                    |GraphQL style output nor expected_output_field_names 
                    |for columnar output; unable to do any further 
                    |processing of execution_input"""
                    .flatten()
            )
        }
    }

    private fun <T> createExpectedOutputFieldNamesPresentWithoutVariablesErrorPublisher(
        session: GraphQLSingleRequestSession
    ): Mono<T> {
        return Mono.error {
            val expectedOutputFieldNames: List<String> =
                session.rawGraphQLRequest.expectedOutputFieldNames
            ServiceError.invalidRequestErrorBuilder()
                .message(
                    """session contains raw_graphql_request.
                |expected_output_field_names [ size: %d, fields: %s ] but 
                |does not have any "variables" that can be used as arguments 
                |to fetch values for these fields 
                |e.g. a user_id to fetch user_info instances"""
                        .flatten(),
                    expectedOutputFieldNames.size,
                    expectedOutputFieldNames.joinToString(", ")
                )
                .build()
        }
    }

    private fun createPreparsedDocumentEntryForExpectedOutputFieldNamesGivenInputVariables(
        executionInput: ExecutionInput,
        session: GraphQLSingleRequestSession
    ): Mono<PreparsedDocumentEntry> {
        val expectedOutputFieldNames: ImmutableList<String> =
            session.rawGraphQLRequest.expectedOutputFieldNames
        return Flux.fromIterable(executionInput.variables.entries)
            .flatMap(determineMatchingParameterPathForVariableEntry(session))
            .flatMap(convertVariableValuesIntoJsonNodeValues())
            .reduce(
                columnarDocumentContextFactory
                    .builder()
                    .expectedFieldNames(expectedOutputFieldNames)
                    .build()
            ) { context, (paramPath: SchematicPath, paramJsonValue: JsonNode) ->
                context.update { addParameterValueForPath(paramPath, paramJsonValue) }
            }
            .flatMap { context: ColumnarDocumentContext ->
                val topLevelSrcIndexPathsSet: PersistentSet<SchematicPath> =
                    context.parameterValuesByPath
                        .asSequence()
                        .map { (path, _) -> SchematicPath.of { pathSegments(path.pathSegments) } }
                        .toPersistentSet()
                Flux.fromIterable(expectedOutputFieldNames)
                    .flatMap(
                        determineMatchingSourceAttributeVertexForFieldNameGivenParameters(
                            topLevelSrcIndexPathsSet,
                            session
                        )
                    )
                    .reduce(context) { ctx, (fieldName: String, path: SchematicPath) ->
                        ctx.update { addSourceIndexPathForFieldName(fieldName, path) }
                    }
            }
            .map(pruneParametersNotNecessaryForFetchingMatchedSourceAttributeVertices())
            .flatMap { context: ColumnarDocumentContext ->
                gatherSourceAttributeVerticesMatchingGivenParameters(
                        context.parameterValuesByPath,
                        session
                    )
                    .concatWith(Flux.fromIterable(context.sourceIndexPathsByFieldName.values))
                    .reduce(persistentSetOf<SchematicPath>()) {
                        ps: PersistentSet<SchematicPath>,
                        srcAttrPath: SchematicPath ->
                        ps.add(srcAttrPath)
                    }
                    .map { sourceIndexPathsSet: PersistentSet<SchematicPath> ->
                        GraphQLQueryPathBasedComposer
                            .createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
                                sourceIndexPathsSet
                            )
                    }
                    .map {
                        queryComposerFunction:
                            (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition ->
                        context.update { queryComposerFunction(queryComposerFunction) }
                    }
            }
            .flatMap { context: ColumnarDocumentContext ->
                context.queryComposerFunction
                    .map { func -> func(context.parameterValuesByPath) }
                    .map { operationDefinition: OperationDefinition ->
                        context.update {
                            operationDefinition(operationDefinition)
                            document(Document.newDocument().definition(operationDefinition).build())
                        }
                    }
                    .toMono()
            }
            .flatMap { context: ColumnarDocumentContext ->
                ParseAndValidate.validate(
                        session.materializationSchema,
                        context.document.orNull()!!
                    )
                    .toMono()
                    .filter(List<ValidationError>::isNotEmpty)
                    .map(::PreparsedDocumentEntry)
                    .switchIfEmpty {
                        Mono.fromSupplier { PreparsedDocumentEntry(context.document.orNull()) }
                    }
                    .doOnNext { entry: PreparsedDocumentEntry ->
                        if (!entry.hasErrors()) {
                            executionInput.graphQLContext.put(
                                ColumnarDocumentContext.COLUMNAR_DOCUMENT_CONTEXT_KEY,
                                context
                            )
                        }
                    }
            }
            .doOnNext(logSuccessStatusOfColumnarInputProcessing())
    }

    private fun determineMatchingParameterPathForVariableEntry(
        session: GraphQLSingleRequestSession
    ): (Map.Entry<String, Any?>) -> Flux<Pair<SchematicPath, Any?>> {
        return { (name, value) ->
            //            when {
            //                session.metamodelGraph.parameterAttributeVerticesByQualifiedName
            //                    .getOrNone(name)
            //                    .filter { paramAttrSet -> paramAttrSet.size >= 1 }
            //                    .isDefined() -> {
            //                    session.metamodelGraph.parameterAttributeVerticesByQualifiedName
            //                        .getOrNone(name)
            //                        .fold(::emptySet, ::identity)
            //                        .asSequence()
            //                        .map { pav: ParameterAttributeVertex -> pav to value }
            //                        .toFlux()
            //                }
            //                session.metamodelGraph.attributeAliasRegistry
            //                    .getParameterVertexPathsWithSimilarNameOrAlias(name)
            //                    .toOption()
            //                    .filter { paramPathSet -> paramPathSet.size >= 1 }
            //                    .isDefined() -> {
            //                    session.metamodelGraph.attributeAliasRegistry
            //                        .getParameterVertexPathsWithSimilarNameOrAlias(name)
            //                        .asSequence()
            //                        .map { paramAttrPath ->
            //
            // session.metamodelGraph.pathBasedGraph.getVertex(paramAttrPath)
            //                        }
            //                        .filterIsInstance<ParameterAttributeVertex>()
            //                        .map { paramAttr -> paramAttr to value }
            //                        .toFlux()
            //                }
            //                else -> {
            //                    Flux.error {
            //                        val message: String =
            //                            """variable [ name: %s ] does not map to any known
            // argument name
            //                                |or alias for an argument"""
            //                                .flatten()
            //                                .format(name)
            //
            // ServiceError.invalidRequestErrorBuilder().message(message).build()
            //                    }
            //                }
            //            }
            TODO("logic for determining matching parameter path")
        }
    }

    private fun convertVariableValuesIntoJsonNodeValues():
        (Pair<SchematicPath, Any?>) -> Mono<Pair<SchematicPath, JsonNode>> {
        return { (path: SchematicPath, paramValue: Any?) ->
            jsonMapper
                .fromKotlinObject<Any?>(paramValue)
                .toJsonNode()
                .toMono()
                .map { jn: JsonNode -> path to jn }
                .onErrorMap { t: Throwable ->
                    ServiceError.builder()
                        .message(
                            """unable to serialize variable value for [ path: %s ]
                                |into JSON"""
                                .flatten(),
                            path
                        )
                        .cause(t)
                        .build()
                }
        }
    }

    private fun determineMatchingSourceAttributeVertexForFieldNameGivenParameters(
        topLevelSrcIndexPathsSet: PersistentSet<SchematicPath>,
        session: GraphQLSingleRequestSession
    ): (String) -> Mono<Pair<String, SchematicPath>> {
        return { fieldName ->
            val camelCaseFieldName: String =
                StandardNamingConventions.CAMEL_CASE.deriveName(fieldName).qualifiedForm
            TODO("logic for determining matching source_attribute_vertex for field_name")
            //            when {
            //                session.metamodelGraph.sourceAttributeVerticesByQualifiedName
            //                    .getOrNone(fieldName)
            //                    .orElse {
            //
            // session.metamodelGraph.sourceAttributeVerticesByQualifiedName.getOrNone(
            //                            camelCaseFieldName
            //                        )
            //                    }
            //                    .filter { srcAttrSet -> srcAttrSet.size == 1 }
            //                    .isDefined() -> {
            //                    session.metamodelGraph.sourceAttributeVerticesByQualifiedName
            //                        .getOrNone(fieldName)
            //                        .orElse {
            //
            // session.metamodelGraph.sourceAttributeVerticesByQualifiedName.getOrNone(
            //                                camelCaseFieldName
            //                            )
            //                        }
            //                        .flatMap { srcAttrSet -> srcAttrSet.firstOrNone() }
            //                        .toMono()
            //                        .map { sav: SourceAttributeVertex -> fieldName to sav }
            //                }
            //                session.metamodelGraph.attributeAliasRegistry
            //                    .getSourceVertexPathWithSimilarNameOrAlias(fieldName)
            //                    .isDefined() -> {
            //                    session.metamodelGraph.attributeAliasRegistry
            //                        .getSourceVertexPathWithSimilarNameOrAlias(fieldName)
            //                        .flatMap { srcAttrPath ->
            //
            // session.metamodelGraph.pathBasedGraph.getVertex(srcAttrPath)
            //                        }
            //                        .filterIsInstance<SourceAttributeVertex>()
            //                        .toMono()
            //                        .map { sav: SourceAttributeVertex -> fieldName to sav }
            //                }
            //                session.metamodelGraph.sourceAttributeVerticesByQualifiedName
            //                    .getOrNone(fieldName)
            //                    .orElse {
            //
            // session.metamodelGraph.sourceAttributeVerticesByQualifiedName.getOrNone(
            //                            camelCaseFieldName
            //                        )
            //                    }
            //                    .filter { srcAttrSet -> srcAttrSet.size > 1 }
            //                    .flatMap { srcAttrSet ->
            //                        /*
            //                         * There exists _just_ one source_attribute with a
            //                         * path that is a descendent of one of the top
            //                         * source_index_paths and if more than one is within that set,
            //                         * there exists one that is shorter than the others
            //                         */
            //                        srcAttrSet
            //                            .asSequence()
            //                            .filter { srcAttr ->
            //                                topLevelSrcIndexPathsSet.any { sp ->
            //                                    srcAttr.path.isDescendentOf(sp)
            //                                }
            //                            }
            //                            .minOfOrNull { srcAttr -> srcAttr.path }
            //                            .toOption()
            //                    }
            //                    .isDefined() -> {
            //                    session.metamodelGraph.sourceAttributeVerticesByQualifiedName
            //                        .getOrNone(fieldName)
            //                        .orElse {
            //
            // session.metamodelGraph.sourceAttributeVerticesByQualifiedName.getOrNone(
            //                                camelCaseFieldName
            //                            )
            //                        }
            //                        .fold(::persistentSetOf, ::identity)
            //                        .asSequence()
            //                        .filter { srcAttr ->
            //                            topLevelSrcIndexPathsSet.any { sp ->
            // srcAttr.path.isDescendentOf(sp) }
            //                        }
            //                        .minByOrNull { srcAttr -> srcAttr.path }
            //                        .toMono()
            //                        .map { sav: SourceAttributeVertex -> fieldName to sav }
            //                }
            //                else -> {
            //                    Mono.error {
            //                        val message: String =
            //                            if (
            //
            // session.metamodelGraph.sourceAttributeVerticesByQualifiedName
            //                                    .getOrNone(fieldName)
            //                                    .orElse {
            //                                        session.metamodelGraph
            //                                            .sourceAttributeVerticesByQualifiedName
            //                                            .getOrNone(camelCaseFieldName)
            //                                    }
            //                                    .filter { srcAttrSet -> srcAttrSet.size > 1 }
            //                                    .isDefined()
            //                            ) {
            //                                """expected_output_field_name [ name: %s ] maps to
            // multiple field_names;
            //                                |an alias for at least one of these field_names needs
            //                                |to be used in place of the configured field
            // name--or--
            //                                |the caller must use a regular GraphQL query in lieu
            // of key-value lookup
            //                                |"""
            //                                    .flatten()
            //                                    .format(fieldName)
            //                            } else {
            //                                """expected_output_field_name [ name: %s ] does not
            // map to any known field_name
            //                                |or alias for a field_name"""
            //                                    .flatten()
            //                                    .format(fieldName)
            //                            }
            //                        ServiceError.of(message)
            //                    }
            //                }
            //            }
        }
    }

    private fun pruneParametersNotNecessaryForFetchingMatchedSourceAttributeVertices():
        (ColumnarDocumentContext) -> ColumnarDocumentContext {
        return { context: ColumnarDocumentContext ->
            val domainPathSegmentSet: PersistentSet<String> =
                context.sourceIndexPathsByFieldName.values
                    .parallelStream()
                    .map { sp: SchematicPath -> sp.pathSegments.firstOrNone() }
                    .flatMapOptions()
                    .reduceToPersistentSet()
            context.parameterValuesByPath.keys
                .asSequence()
                .filterNot { paramPath ->
                    paramPath.pathSegments
                        .firstOrNone()
                        .filter { domainPathSegment -> domainPathSegment in domainPathSegmentSet }
                        .isDefined()
                }
                .fold(context) { ctx, paramPath ->
                    ctx.update { removeParameterValueWithPath(paramPath) }
                }
        }
    }

    private fun gatherSourceAttributeVerticesMatchingGivenParameters(
        parameterMap: ImmutableMap<SchematicPath, JsonNode>,
        session: GraphQLSingleRequestSession
    ): Flux<SchematicPath> {
        return Flux.fromIterable(parameterMap.keys).flatMap { parameterPath ->
            // ParameterToSourceAttributeVertexMatcher(session.materializationMetamodel,
            // parameterPath)
            //    .toMono()
            Mono.error<SchematicPath> {
                TODO("logic for calculating source_attribute_vertices matching given parameters")
            }
        }
    }

    private fun logSuccessStatusOfColumnarInputProcessing(): (PreparsedDocumentEntry) -> Unit {
        return { entry: PreparsedDocumentEntry ->
            val status: String =
                if (entry.hasErrors()) {
                    "failed"
                } else {
                    "success"
                }
            val output: String =
                if (entry.hasErrors()) {
                    entry.errors.joinToString(
                        separator = ",\n",
                        prefix = "{ ",
                        postfix = " }",
                        transform = { e ->
                            "[ type: %s, message: %s ]".format(e.errorType, e.message)
                        }
                    )
                } else {
                    "\n" +
                        AstPrinter.printAst(
                            entry.document.definitions
                                .filterIsInstance<OperationDefinition>()
                                .firstOrNull()
                        )
                }
            val message: String =
                """create_preparsed_document_entry_for_expected_output_
                |field_names_given_input_variables: [ status: {} ]
                |[ output: {} ]
                |"""
                    .flatten()
            logger.info(message, status, output)
        }
    }
}
