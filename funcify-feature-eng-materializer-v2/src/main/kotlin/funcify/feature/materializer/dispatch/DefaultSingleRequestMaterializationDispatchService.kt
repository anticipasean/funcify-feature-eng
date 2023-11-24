package funcify.feature.materializer.dispatch

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.graph.line.DirectedLine
import funcify.feature.materializer.dispatch.context.DefaultDispatchedRequestMaterializationGraphContextFactory
import funcify.feature.materializer.dispatch.context.DispatchedRequestMaterializationGraphContext
import funcify.feature.materializer.dispatch.context.DispatchedRequestMaterializationGraphContextFactory
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.json.JsonNodeValueExtractionByOperationPath
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.tracking.TrackableValueFactory
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.PairExtensions.bimap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StreamExtensions.recurseBreadthFirst
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.foldIntoTry
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.tryFold
import funcify.feature.tools.extensions.TryExtensions.tryReduce
import funcify.feature.tools.json.JsonMapper
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.execution.RawVariables
import graphql.execution.ValuesResolver
import graphql.language.Definition
import graphql.language.Document
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.*
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.stream.Stream

internal class DefaultSingleRequestMaterializationDispatchService(
    private val jsonMapper: JsonMapper,
    private val dispatchedRequestMaterializationGraphContextFactory:
        DispatchedRequestMaterializationGraphContextFactory =
        DefaultDispatchedRequestMaterializationGraphContextFactory(),
    private val trackableValueFactory: TrackableValueFactory
) : SingleRequestMaterializationDispatchService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationDispatchService>()
        private const val METHOD_TAG: String =
            "dispatch_requests_in_materialization_graph_in_session"
        private const val DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS: Int = 4
        private val DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION: Duration =
            Duration.ofSeconds(DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS.toLong())
    }

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession> {
        logger.info("$METHOD_TAG: [ session.session_id: ${session.sessionId} ]")
        return Mono.defer {
                when (
                    val requestMaterializationGraph: RequestMaterializationGraph? =
                        session.requestMaterializationGraph.orNull()
                ) {
                    null -> {
                        requestMaterializationGraphNotDefinedInSessionErrorPublisher(session)
                    }
                    else -> {
                        dispatchRequestsForCallablesInRequestMaterializationGraph(
                            session,
                            requestMaterializationGraph
                        )
                    }
                }
            }
            .doOnNext(dispatchedRequestMaterializationGraphSuccessLogger())
            .doOnError(dispatchedRequestMaterializationGraphFailureLogger())
    }

    private fun requestMaterializationGraphNotDefinedInSessionErrorPublisher(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        return Mono.error {
            ServiceError.of(
                "request_materialization_graph has not been defined in session [ session.session_id: {} ]",
                session.sessionId
            )
        }
    }

    private fun dispatchRequestsForCallablesInRequestMaterializationGraph(
        session: GraphQLSingleRequestSession,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Mono<out GraphQLSingleRequestSession> {
        return createDispatchedRequestMaterializationGraph(session, requestMaterializationGraph)
            .map { drmg: DispatchedRequestMaterializationGraph ->
                session.update { dispatchedRequestMaterializationGraph(drmg) }
            }
    }

    private fun createDispatchedRequestMaterializationGraph(
        session: GraphQLSingleRequestSession,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Mono<out DispatchedRequestMaterializationGraph> {
        return Mono.fromCallable {
                when (val ric: RawInputContext? = session.rawInputContext.orNull()) {
                    null -> {
                        dispatchedRequestMaterializationGraphContextFactory
                            .builder()
                            .rawGraphQLRequest(session.rawGraphQLRequest)
                            .materializationMetamodel(session.materializationMetamodel)
                            .requestMaterializationGraph(requestMaterializationGraph)
                            .build()
                    }
                    else -> {
                        dispatchedRequestMaterializationGraphContextFactory
                            .builder()
                            .rawGraphQLRequest(session.rawGraphQLRequest)
                            .materializationMetamodel(session.materializationMetamodel)
                            .requestMaterializationGraph(requestMaterializationGraph)
                            .rawInputContext(ric)
                            .build()
                    }
                }
            }
            .flatMap(coerceVariablesAndExtractPassThruColumnsWithinContext())
            .flatMap(dispatchAllTransformerCallablesWithinContext())
            .flatMap(dispatchAllDataElementCallablesWithinContext())
            .flatMap(dispatchAllFeatureCalculatorCallablesWithinContext())
            .map(createDispatchedRequestMaterializationGraphFromContext())
    }

    private fun <C> coerceVariablesAndExtractPassThruColumnsWithinContext():
        (C) -> Mono<out DispatchedRequestMaterializationGraphContext> where
    C : DispatchedRequestMaterializationGraphContext {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            coerceVariablesFromSession(
                    drmgc.rawGraphQLRequest,
                    drmgc.materializationMetamodel,
                    drmgc.requestMaterializationGraph
                )
                .zip(
                    drmgc.requestMaterializationGraph.passThruColumns
                        .asSequence()
                        .map { c: String ->
                            drmgc.rawInputContext
                                .flatMap { ric: RawInputContext -> ric.get(c) }
                                .map { jn: JsonNode -> c to jn }
                                .successIfDefined {
                                    ServiceError.of(
                                        "passthru_column [ %s ] expected but not found in raw_input_context set",
                                        c
                                    )
                                }
                        }
                        .tryFold(
                            persistentMapOf<String, JsonNode>(),
                            PersistentMap<String, JsonNode>::plus
                        )
                ) { cv: CoercedVariables, ptc: PersistentMap<String, JsonNode> ->
                    drmgc.update {
                        coercedVariables(cv)
                        addAllPassThruColumns(ptc)
                    }
                }
                .toMono()
        }
    }

    private fun coerceVariablesFromSession(
        rawGraphQLRequest: RawGraphQLRequest,
        materializationMetamodel: MaterializationMetamodel,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Try<CoercedVariables> {
        return Try.attempt { RawVariables.of(rawGraphQLRequest.variables) }
            .map { rv: RawVariables ->
                ValuesResolver.coerceVariableValues(
                    materializationMetamodel.materializationGraphQLSchema,
                    requestMaterializationGraph.operationName
                        .filter(String::isNotBlank)
                        .flatMap { on: String ->
                            requestMaterializationGraph.preparsedDocumentEntry.document
                                .getOperationDefinition(on)
                                .toOption()
                        }
                        .orElse {
                            requestMaterializationGraph.preparsedDocumentEntry.document
                                .toOption()
                                .mapNotNull(Document::getDefinitions)
                                .map(List<Definition<*>>::asSequence)
                                .fold(::emptySequence, ::identity)
                                .filterIsInstance<OperationDefinition>()
                                .firstOrNone()
                        }
                        .mapNotNull { od: OperationDefinition -> od.variableDefinitions }
                        .fold(::emptyList, ::identity),
                    rv,
                    GraphQLContext.getDefault(),
                    rawGraphQLRequest.locale
                )
            }
            .mapFailure { t: Throwable ->
                ServiceError.builder()
                    .message(
                        "error occurred when coercing variables into instances of [ type: %s ]",
                        Value::class.qualifiedName
                    )
                    .cause(t)
                    .build()
            }
    }

    private fun <C> dispatchAllTransformerCallablesWithinContext():
        (C) -> Mono<out DispatchedRequestMaterializationGraphContext> where
    C : DispatchedRequestMaterializationGraphContext {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.transformerCallablesByPath.asIterable()
                )
                .reduce(drmgc) {
                    c: DispatchedRequestMaterializationGraphContext,
                    (p: GQLOperationPath, tc: TransformerCallable) ->
                    dispatchTransformerCallable(c, p, tc)
                }
        }
    }

    private fun dispatchTransformerCallable(
        context: DispatchedRequestMaterializationGraphContext,
        path: GQLOperationPath,
        transformerCallable: TransformerCallable
    ): DispatchedRequestMaterializationGraphContext {
        return when {
            path !in context.requestMaterializationGraph.requestGraph -> {
                throw ServiceError.of(
                    "unable to dispatch transformer_callable for [ path: %s ]",
                    path
                )
            }
            else -> {
                context.requestMaterializationGraph.requestGraph
                    .edgesFromPoint(path)
                    .asSequence()
                    .map(extractArgumentPathNameValueTripleForTransformerEdge(context, path))
                    .tryFold(
                        persistentMapOf<GQLOperationPath, JsonNode>() to
                            persistentMapOf<String, JsonNode>()
                    ) { maps, (p: GQLOperationPath, n: String, jn: JsonNode) ->
                        maps.bimap(
                            { argValsByPath -> argValsByPath.put(p, jn) },
                            { argValsByName -> argValsByName.put(n, jn) }
                        )
                    }
                    .map { (byPath, byName) ->
                        context.update {
                            addAllMaterializedArguments(byPath)
                            addTransformerPublisherForResultPath(
                                GQLResultPath.fromOperationPathOrThrow(path),
                                transformerCallable.invoke(byName).cache()
                            )
                        }
                    }
                    .orElseThrow()
            }
        }
    }

    private fun extractArgumentPathNameValueTripleForTransformerEdge(
        context: DispatchedRequestMaterializationGraphContext,
        transformerSourcePath: GQLOperationPath,
    ): (Pair<DirectedLine<GQLOperationPath>, MaterializationEdge>) -> Try<
            Triple<GQLOperationPath, String, JsonNode>
        > {
        return { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
            if (!l.destinationPoint.refersToArgument()) {
                ServiceError.of(
                        "dependent [ %s ] for transformer_source [ path: %s ] is not an argument",
                        l.destinationPoint,
                        transformerSourcePath
                    )
                    .failure()
            } else {
                when (e) {
                    MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED,
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                        context.requestMaterializationGraph.requestGraph
                            .get(l.destinationPoint)
                            .toOption()
                            .filterIsInstance<FieldArgumentComponentContext>()
                            .flatMap { facc: FieldArgumentComponentContext ->
                                GraphQLValueToJsonNodeConverter.invoke(facc.argument.value).map {
                                    jn: JsonNode ->
                                    Triple(facc.path, facc.argument.name, jn)
                                }
                            }
                            .successIfDefined {
                                ServiceError.of(
                                    "unable to extract direct or default argument.value as json for argument [ path: %s ]",
                                    l.destinationPoint
                                )
                            }
                    }
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                        context.requestMaterializationGraph.requestGraph
                            .get(l.destinationPoint)
                            .toOption()
                            .filterIsInstance<FieldArgumentComponentContext>()
                            .flatMap { facc: FieldArgumentComponentContext ->
                                facc.argument.value
                                    .toOption()
                                    .filterIsInstance<VariableReference>()
                                    .mapNotNull { vr: VariableReference -> vr.name }
                                    .map { n: String ->
                                        // TODO: Consider empty value generator e.g. List => [],
                                        // Int? => null before GraphQLValueToJsonNodeConverter
                                        // application
                                        context.coercedVariables
                                            .get(n)
                                            .toOption()
                                            .filterIsInstance<Value<*>>()
                                            .flatMap(GraphQLValueToJsonNodeConverter)
                                            .getOrElse { JsonNodeFactory.instance.nullNode() }
                                    }
                                    .map { jn: JsonNode ->
                                        Triple(facc.path, facc.argument.name, jn)
                                    }
                            }
                            .successIfDefined {
                                ServiceError.of(
                                    "unable to extract argument value for [ path: %s ]",
                                    l.destinationPoint
                                )
                            }
                    }
                    else -> {
                        ServiceError.of(
                                "strategy for determining argument value for [ path: %s ] not available",
                                transformerSourcePath
                            )
                            .failure()
                    }
                }
            }
        }
    }

    private fun <C> dispatchAllDataElementCallablesWithinContext():
        (C) -> Mono<out DispatchedRequestMaterializationGraphContext> where
    C : DispatchedRequestMaterializationGraphContext {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.dataElementCallablesByPath.asIterable()
                )
                .reduce(drmgc) {
                    c: DispatchedRequestMaterializationGraphContext,
                    (p: GQLOperationPath, dec: DataElementCallable) ->
                    dispatchDataElementCallable(c, p, dec)
                }
        }
    }

    private fun dispatchDataElementCallable(
        context: DispatchedRequestMaterializationGraphContext,
        path: GQLOperationPath,
        dataElementCallable: DataElementCallable
    ): DispatchedRequestMaterializationGraphContext {
        return when {
            path !in context.requestMaterializationGraph.requestGraph -> {
                throw ServiceError.of(
                    "unable to dispatch data_element_callable for [ path: %s ]",
                    path
                )
            }
            else -> {
                context.requestMaterializationGraph.requestGraph
                    .edgesFromPoint(path)
                    .asSequence()
                    .flatMap(extractArgumentNamePathValueTripleForDataElementEdge(path, context))
                    .tryFold(persistentMapOf<GQLOperationPath, JsonNode>()) {
                        pm,
                        (p: GQLOperationPath, n: String, jn: JsonNode) ->
                        pm.put(p, jn)
                    }
                    .map { m: PersistentMap<GQLOperationPath, JsonNode> ->
                        val dataElementPublisher: Mono<JsonNode> =
                            dataElementCallable.invoke(m).cache()
                        context.update {
                            addAllMaterializedArguments(m)
                            addDataElementPublisherForOperationPath(path, dataElementPublisher)
                            addDataElementPublisherForResultPath(
                                GQLResultPath.fromOperationPathOrThrow(path),
                                dataElementPublisher
                            )
                        }
                    }
                    .orElseThrow()
            }
        }
    }

    private fun extractArgumentNamePathValueTripleForDataElementEdge(
        path: GQLOperationPath,
        context: DispatchedRequestMaterializationGraphContext,
    ): (Pair<DirectedLine<GQLOperationPath>, MaterializationEdge>) -> Sequence<
            Try<Triple<GQLOperationPath, String, JsonNode>>
        > {
        return { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
            if (!l.destinationPoint.refersToArgument()) {
                sequenceOf(
                    ServiceError.of(
                            "dependent [ %s ] for data_element_source [ path: %s ] is not an argument",
                            l.destinationPoint,
                            path
                        )
                        .failure()
                )
            } else {
                when (e) {
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                        sequenceOf(
                            context.requestMaterializationGraph.requestGraph
                                .get(l.destinationPoint)
                                .toOption()
                                .filterIsInstance<FieldArgumentComponentContext>()
                                .flatMap { facc: FieldArgumentComponentContext ->
                                    GraphQLValueToJsonNodeConverter.invoke(facc.argument.value)
                                        .map { jn: JsonNode -> facc.argument.name to jn }
                                }
                                .map { (n: String, jn: JsonNode) ->
                                    Triple(l.destinationPoint, n, jn)
                                }
                                .successIfDefined {
                                    ServiceError.of(
                                        "unable to extract default argument.value as json for argument [ path: %s ]",
                                        l.destinationPoint
                                    )
                                }
                        )
                    }
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                        sequenceOf(
                            context.requestMaterializationGraph.requestGraph
                                .get(l.destinationPoint)
                                .toOption()
                                .filterIsInstance<FieldArgumentComponentContext>()
                                .flatMap { facc: FieldArgumentComponentContext ->
                                    facc.argument.value
                                        .toOption()
                                        .filterIsInstance<VariableReference>()
                                        .map { vr: VariableReference -> vr.name }
                                        .map { n: String ->
                                            context.coercedVariables
                                                .get(n)
                                                .toOption()
                                                .filterIsInstance<Value<*>>()
                                                .getOrElse { NullValue.of() }
                                        }
                                        .flatMap(GraphQLValueToJsonNodeConverter)
                                        .map { jn: JsonNode -> facc.argument.name to jn }
                                }
                                .map { (n: String, jn: JsonNode) ->
                                    Triple(l.destinationPoint, n, jn)
                                }
                                .successIfDefined {
                                    ServiceError.of(
                                        "unable to extract argument value for [ path: %s ]",
                                        l.destinationPoint
                                    )
                                }
                        )
                    }
                    MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                        extractArgumentNamePathValueTripleForRawInputValueProvidedDataElementEdge(
                            context,
                            path,
                            l.destinationPoint
                        )
                    }
                    else -> {
                        sequenceOf(
                            ServiceError.of(
                                    "strategy for determining argument value for [ path: %s ] not available",
                                    path
                                )
                                .failure()
                        )
                    }
                }
            }
        }
    }

    private fun extractArgumentNamePathValueTripleForRawInputValueProvidedDataElementEdge(
        context: DispatchedRequestMaterializationGraphContext,
        domainDataElementPath: GQLOperationPath,
        dataElementArgumentPath: GQLOperationPath,
    ): Sequence<Try<Triple<GQLOperationPath, String, JsonNode>>> {
        return context.requestMaterializationGraph.requestGraph
            .get(domainDataElementPath)
            .toOption()
            .filterIsInstance<SelectedFieldComponentContext>()
            .successIfDefined {
                ServiceError.of(
                    "%s has not been provided in request_graph for [ path: %s ]",
                    SelectedFieldComponentContext::class.simpleName,
                    domainDataElementPath
                )
            }
            .flatMap { sfcc: SelectedFieldComponentContext ->
                context.rawInputContext
                    .flatMap { ric: RawInputContext ->
                        // TODO: Add lookup by alias here to for raw_input_context
                        // if decided worthwhile
                        ric.get(sfcc.fieldCoordinates.fieldName).map { jn: JsonNode ->
                            Triple(domainDataElementPath, sfcc.fieldCoordinates.fieldName, jn)
                        }
                    }
                    .successIfDefined {
                        ServiceError.of(
                            "raw_input_context does not contain value for key [ coordinates: %s ]",
                            sfcc.fieldCoordinates
                        )
                    }
                    .map { t: Triple<GQLOperationPath, String, JsonNode> ->
                        sequenceOf(
                            Try.success(t),
                            extractFieldValueForDataElementArgumentInRawInputJSON(
                                context,
                                sfcc.fieldCoordinates,
                                t.third,
                                dataElementArgumentPath
                            )
                        )
                    }
            }
            .fold(::identity) { t: Throwable -> sequenceOf(Try.failure(t)) }
    }

    private fun extractFieldValueForDataElementArgumentInRawInputJSON(
        context: DispatchedRequestMaterializationGraphContext,
        domainDataElementFieldCoordinates: FieldCoordinates,
        domainDataElementJSON: JsonNode,
        dataElementArgumentPath: GQLOperationPath
    ): Try<Triple<GQLOperationPath, String, JsonNode>> {
        return context.requestMaterializationGraph.requestGraph
            .get(dataElementArgumentPath)
            .toOption()
            .filterIsInstance<FieldArgumentComponentContext>()
            .successIfDefined {
                ServiceError.of(
                    "%s has not been provided in request_graph for [ path: %s ]",
                    FieldArgumentComponentContext::class.simpleName,
                    dataElementArgumentPath
                )
            }
            .flatMap { facc: FieldArgumentComponentContext ->
                context.materializationMetamodel.domainSpecifiedDataElementSourceByCoordinates
                    .getOrNone(domainDataElementFieldCoordinates)
                    .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                        dsdes.argumentsByName.getOrNone(facc.argument.name).flatMap {
                            ga: GraphQLArgument ->
                            dsdes.domainFieldDefinition.type
                                .toOption()
                                .mapNotNull(GraphQLTypeUtil::unwrapAll)
                                .filterIsInstance<GraphQLFieldsContainer>()
                                .flatMap { gfc: GraphQLFieldsContainer ->
                                    gfc.getFieldDefinition(ga.name).toOption().orElse {
                                        context.materializationMetamodel.aliasCoordinatesRegistry
                                            .getFieldsWithAlias(ga.name)
                                            .asSequence()
                                            .firstOrNone { fc: FieldCoordinates ->
                                                fc.typeName == gfc.name &&
                                                    gfc.getFieldDefinition(fc.fieldName) != null
                                            }
                                            .flatMap { fc: FieldCoordinates ->
                                                gfc.getFieldDefinition(fc.fieldName).toOption()
                                            }
                                    }
                                }
                                .flatMap { gfd: GraphQLFieldDefinition ->
                                    JsonNodeValueExtractionByOperationPath.invoke(
                                        domainDataElementJSON,
                                        GQLOperationPath.of { field(gfd.name) }
                                    )
                                }
                                .map { jn: JsonNode -> Triple(facc.path, ga.name, jn) }
                        }
                    }
                    .successIfDefined {
                        ServiceError.of(
                            """could not find JSON value for argument [ path: %s ] 
                                |within domain data_element JSON for %s"""
                                .flatten(),
                            facc.path,
                            DomainSpecifiedDataElementSource::class.simpleName
                        )
                    }
            }
    }

    private fun <C> dispatchAllFeatureCalculatorCallablesWithinContext():
        (C) -> Mono<out DispatchedRequestMaterializationGraphContext> where
    C : DispatchedRequestMaterializationGraphContext {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.featureCalculatorCallablesByPath.asIterable()
                )
                .sort(bringForwardIndependentFeatureCalculationsComparator(drmgc))
                .reduce(drmgc) {
                    c: DispatchedRequestMaterializationGraphContext,
                    (p: GQLOperationPath, fcc: FeatureCalculatorCallable) ->
                    dispatchFeatureCalculatorCallable(c, p, fcc)
                }
                .doOnNext { c: DispatchedRequestMaterializationGraphContext ->
                    logger.debug(
                        "feature_calculator_callables created: operation_paths: {}, result_paths: {}",
                        c.featureCalculatorPublishersByOperationPath.keys,
                        c.featureCalculatorPublishersByResultPath.keys
                    )
                }
        }
    }

    private fun bringForwardIndependentFeatureCalculationsComparator(
        drmgc: DispatchedRequestMaterializationGraphContext
    ): Comparator<Map.Entry<GQLOperationPath, FeatureCalculatorCallable>> {
        return Comparator { (p1, fcc1), (p2, fcc2) ->
            when {
                drmgc.requestMaterializationGraph.requestGraph
                    .edgesFromPointAsStream(p1)
                    .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        drmgc.requestMaterializationGraph.requestGraph.edgesFromPointAsStream(
                            l.destinationPoint
                        )
                    }
                    .anyMatch { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        l.destinationPoint == p2
                    } -> {
                    1
                }
                drmgc.requestMaterializationGraph.requestGraph
                    .edgesFromPointAsStream(p2)
                    .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        drmgc.requestMaterializationGraph.requestGraph.edgesFromPointAsStream(
                            l.destinationPoint
                        )
                    }
                    .anyMatch { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        l.destinationPoint == p1
                    } -> {
                    -1
                }
                Stream.of(p1)
                    .recurseBreadthFirst { p: GQLOperationPath ->
                        drmgc.requestMaterializationGraph.requestGraph
                            .edgesFromPointAsStream(p)
                            .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge)
                                ->
                                drmgc.requestMaterializationGraph.requestGraph
                                    .edgesFromPointAsStream(l.destinationPoint)
                            }
                            .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge)
                                ->
                                when (l.destinationPoint) {
                                    p2 -> {
                                        Stream.of(l.destinationPoint.right())
                                    }
                                    in drmgc.requestMaterializationGraph
                                        .featureCalculatorCallablesByPath -> {
                                        Stream.of(l.destinationPoint.left())
                                    }
                                    else -> {
                                        Stream.empty()
                                    }
                                }
                            }
                    }
                    .count() > 0 -> {
                    1
                }
                Stream.of(p2)
                    .recurseBreadthFirst { p: GQLOperationPath ->
                        drmgc.requestMaterializationGraph.requestGraph
                            .edgesFromPointAsStream(p)
                            .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge)
                                ->
                                drmgc.requestMaterializationGraph.requestGraph
                                    .edgesFromPointAsStream(l.destinationPoint)
                            }
                            .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge)
                                ->
                                when (l.destinationPoint) {
                                    p1 -> {
                                        Stream.of(l.destinationPoint.right())
                                    }
                                    in drmgc.requestMaterializationGraph
                                        .featureCalculatorCallablesByPath -> {
                                        Stream.of(l.destinationPoint.left())
                                    }
                                    else -> {
                                        Stream.empty()
                                    }
                                }
                            }
                    }
                    .count() > 0 -> {
                    -1
                }
                else -> {
                    0
                }
            }
        }
    }

    private fun dispatchFeatureCalculatorCallable(
        context: DispatchedRequestMaterializationGraphContext,
        path: GQLOperationPath,
        featureCalculatorCallable: FeatureCalculatorCallable,
    ): DispatchedRequestMaterializationGraphContext {
        return context.requestMaterializationGraph.featureArgumentGroupsByPath
            .invoke(path)
            .asSequence()
            .withIndex()
            .foldIntoTry(context) {
                c: DispatchedRequestMaterializationGraphContext,
                (argGroupIndex: Int, argGroup: ImmutableMap<String, GQLOperationPath>) ->
                logger.debug(
                    "feature_argument_groups: [ path: {}, argument_group_index: {}, argument_group: {} ]",
                    path,
                    argGroupIndex,
                    argGroup.asSequence().joinToString(", ", "{ ", " } ") { (n, p) -> "${n}: ${p}" }
                )
                createTrackableJsonValueForFeatureCalculation(
                        c,
                        argGroupIndex,
                        argGroup,
                        path,
                        featureCalculatorCallable
                    )
                    .zip(
                        createArgumentPublishersMapForFeatureCalculation(
                            c,
                            argGroupIndex,
                            argGroup,
                            path,
                            featureCalculatorCallable
                        )
                    ) {
                        tv: TrackableValue.PlannedValue<JsonNode>,
                        ap: ImmutableMap<GQLOperationPath, Mono<JsonNode>> ->
                        val featurePublisher: Mono<TrackableValue<JsonNode>> =
                            featureCalculatorCallable.invoke(tv, ap).cache()
                        c.update {
                            addPlannedFeatureValue(path, tv)
                            addFeatureCalculatorPublisherForOperationPath(path, featurePublisher)
                            addFeatureCalculatorPublisherForResultPath(
                                GQLResultPath.fromOperationPathOrThrow(path),
                                featurePublisher
                            )
                        }
                    }
                    .orElseThrow()
            }
            .orElseThrow()
        // TODO: Add handling for no arg groups found for feature path scenario
    }

    private fun createTrackableJsonValueForFeatureCalculation(
        context: DispatchedRequestMaterializationGraphContext,
        argumentGroupIndex: Int,
        argumentGroup: ImmutableMap<String, GQLOperationPath>,
        path: GQLOperationPath,
        featureCalculatorCallable: FeatureCalculatorCallable
    ): Try<TrackableValue.PlannedValue<JsonNode>> {
        val CREATE_TV_METHOD_TAG: String = "create_trackable_json_value_for_feature_calculation"
        val dependentArgPaths: ImmutableSet<GQLOperationPath> =
            context.requestMaterializationGraph.featureArgumentDependenciesSetByPathAndIndex.invoke(
                path,
                argumentGroupIndex
            )
        logger.debug(
            "${CREATE_TV_METHOD_TAG}: [ path: {}, dependent_args: {} ]",
            path,
            dependentArgPaths
        )
        return when {
            dependentArgPaths.isEmpty() &&
                featureCalculatorCallable.argumentsByPath.isNotEmpty() -> {
                ServiceError.of(
                        """feature not linked to any arguments within 
                        |request_materialization_graph, but feature_calculator_callable 
                        |expects arguments [ path: %s, args.name: %s ]"""
                            .flatten(),
                        featureCalculatorCallable.featurePath,
                        featureCalculatorCallable.argumentsByName.keys
                    )
                    .failure()
            }
            else -> {
                dependentArgPaths
                    .asSequence()
                    .map { p: GQLOperationPath ->
                        context.materializedArgumentsByPath
                            .getOrNone(p)
                            .successIfDefined {
                                ServiceError.of(
                                    """materialized argument value not present for 
                                    |[ path: %s ]; necessary for calculation of feature 
                                    |[ path: %s ]"""
                                        .flatten(),
                                    p,
                                    path
                                )
                            }
                            .flatMap { jn: JsonNode ->
                                // TODO: Consider providing whatever field.name argument.name
                                // corresponds to within return type
                                context.requestMaterializationGraph.requestGraph
                                    .get(p)
                                    .toOption()
                                    .filterIsInstance<FieldArgumentComponentContext>()
                                    .map { facc: FieldArgumentComponentContext ->
                                        facc.argument.name
                                    }
                                    .successIfDefined {
                                        ServiceError.of(
                                            "argument.name not found for materialized argument path [ %s ]",
                                            p
                                        )
                                    }
                                    .map { name: String -> name to jn }
                            }
                    }
                    .tryFold(
                        trackableValueFactory
                            .builder()
                            .graphQLOutputType(
                                featureCalculatorCallable.featureGraphQLFieldDefinition.type
                            )
                            .operationPath(featureCalculatorCallable.featurePath)
                    ) { b: TrackableValue.PlannedValue.Builder, (n: String, jn: JsonNode) ->
                        b.addContextualParameter(n, jn)
                    }
                    .flatMap { b: TrackableValue.PlannedValue.Builder ->
                        b.buildForInstanceOf<JsonNode>()
                    }
                    .peek(
                        { tv: TrackableValue.PlannedValue<JsonNode> ->
                            logger.info(
                                "${CREATE_TV_METHOD_TAG}: [ status: successful ][ trackable_value: {} ]",
                                tv
                            )
                        },
                        { t: Throwable ->
                            logger.info(
                                "${CREATE_TV_METHOD_TAG}: [ status: failed ][ type: {}, message: {} ]",
                                t.toOption()
                                    .filterIsInstance<ServiceError>()
                                    .and(ServiceError::class.simpleName.toOption())
                                    .orElse { t::class.simpleName.toOption() }
                                    .getOrElse { "<NA>" },
                                t.message
                            )
                        }
                    )
            }
        }
    }

    private fun createArgumentPublishersMapForFeatureCalculation(
        context: DispatchedRequestMaterializationGraphContext,
        argumentGroupIndex: Int,
        argumentGroup: ImmutableMap<String, GQLOperationPath>,
        featurePath: GQLOperationPath,
        featureCalculatorCallable: FeatureCalculatorCallable
    ): Try<ImmutableMap<GQLOperationPath, Mono<JsonNode>>> {
        return argumentGroup
            .asSequence()
            .map { (n: String, p: GQLOperationPath) ->
                val edges: ImmutableSet<MaterializationEdge> =
                    context.requestMaterializationGraph.requestGraph
                        .get(featurePath, p)
                        .toPersistentSet()
                when {
                    edges.isEmpty() -> {
                        ServiceError.of(
                                "edge not found for [ feature_path: %s, argument_path: %s ]",
                                featurePath,
                                p
                            )
                            .failure()
                    }
                    edges.size > 1 -> {
                        ServiceError.of(
                                "more than one edge found for [ feature_path: %s, argument_path: %s ]",
                                featurePath,
                                p
                            )
                            .failure()
                    }
                    else -> {
                        Try.fromOption(edges.firstOrNone()).map { e: MaterializationEdge -> p to e }
                    }
                }
            }
            .tryFold(persistentMapOf<GQLOperationPath, Mono<JsonNode>>()) {
                pm: PersistentMap<GQLOperationPath, Mono<JsonNode>>,
                (p: GQLOperationPath, e: MaterializationEdge) ->
                when (e) {
                        MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                            context.materializedArgumentsByPath
                                .getOrNone(p)
                                .orElse {
                                    context.requestMaterializationGraph.requestGraph
                                        .get(p)
                                        .toOption()
                                        .filterIsInstance<FieldArgumentComponentContext>()
                                        .flatMap { facc: FieldArgumentComponentContext ->
                                            featureCalculatorCallable.featureGraphQLFieldDefinition
                                                .getArgument(facc.argument.name)
                                                .toOption()
                                                .mapNotNull { ga: GraphQLArgument ->
                                                    ga.argumentDefaultValue
                                                }
                                                .mapNotNull(InputValueWithState::getValue)
                                                .filterIsInstance<Value<*>>()
                                                .flatMap(GraphQLValueToJsonNodeConverter)
                                        }
                                }
                                .map { jn: JsonNode -> p to Mono.just(jn) }
                                .successIfDefined {
                                    ServiceError.of(
                                        """unable to extract default value for 
                                        |argument [ path: %s ] for feature calculation 
                                        |[ path %s ]"""
                                            .flatten(),
                                        p,
                                        featurePath
                                    )
                                }
                        }
                        MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                            context.materializedArgumentsByPath
                                .getOrNone(p)
                                .orElse {
                                    context.requestMaterializationGraph.requestGraph
                                        .get(p)
                                        .toOption()
                                        .filterIsInstance<FieldArgumentComponentContext>()
                                        .map { facc: FieldArgumentComponentContext ->
                                            facc.argument.value
                                        }
                                        .filterIsInstance<VariableReference>()
                                        .map { vr: VariableReference -> vr.name }
                                        .flatMap { n: String ->
                                            context.coercedVariables
                                                .get(n)
                                                .toOption()
                                                .filterIsInstance<Value<*>>()
                                                .flatMap(GraphQLValueToJsonNodeConverter)
                                        }
                                }
                                .map { jn: JsonNode -> p to Mono.just(jn) }
                                .successIfDefined {
                                    ServiceError.of(
                                        """unable to extract argument value for 
                                        |[ path: %s ] for feature calculation 
                                        |[ path: %s ]"""
                                            .flatten(),
                                        p,
                                        featurePath
                                    )
                                }
                        }
                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                            context.requestMaterializationGraph.requestGraph
                                .get(p)
                                .toOption()
                                .filterIsInstance<SelectedFieldComponentContext>()
                                .zip(context.rawInputContext)
                                .flatMap {
                                    (sfcc: SelectedFieldComponentContext, ric: RawInputContext) ->
                                    ric.get(sfcc.fieldCoordinates.fieldName)
                                }
                                // TODO: Refine extraction of value within json_node value
                                .map { jn: JsonNode -> featurePath to Mono.just(jn) }
                                .successIfDefined {
                                    ServiceError.of(
                                        "raw_input_context does not contain value for key [ field_name: %s ] necessary for feature calculation [ path: %s ]",
                                        context.requestMaterializationGraph.requestGraph
                                            .get(featurePath)
                                            .toOption()
                                            .filterIsInstance<SelectedFieldComponentContext>()
                                            .map { sfcc: SelectedFieldComponentContext ->
                                                sfcc.fieldCoordinates.fieldName
                                            }
                                            .getOrElse { "<NA>" },
                                        featurePath
                                    )
                                }
                        }
                        MaterializationEdge.EXTRACT_FROM_SOURCE -> {
                            createArgumentPublisherForDependentDataElementOrFeature(
                                context,
                                p,
                                argumentGroupIndex
                            )
                        }
                        else -> {
                            ServiceError.of(
                                    "unhandled connection type from feature [ path: %s ] to its argument [ path: %s ]",
                                    featurePath,
                                    p
                                )
                                .failure()
                        }
                    }
                    .map { ppp: Pair<GQLOperationPath, Mono<JsonNode>> -> pm.plus(ppp) }
                    .orElseThrow()
            }
    }

    private fun createArgumentPublisherForDependentDataElementOrFeature(
        context: DispatchedRequestMaterializationGraphContext,
        argumentPath: GQLOperationPath,
        argumentGroupIndex: Int,
    ): Try<Pair<GQLOperationPath, Mono<JsonNode>>> {
        return context.requestMaterializationGraph.requestGraph
            .edgesFromPointAsStream(argumentPath)
            .filter { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                l.destinationPoint !in
                    context.requestMaterializationGraph.featureCalculatorCallablesByPath &&
                    e == MaterializationEdge.EXTRACT_FROM_SOURCE
            }
            .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                context.requestMaterializationGraph.requestGraph.edgesFromPointAsStream(
                    l.destinationPoint
                )
            }
            .map { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                when {
                    e == MaterializationEdge.EXTRACT_FROM_SOURCE &&
                        l.destinationPoint in context.dataElementPublishersByOperationPath -> {
                        Try.success(l)
                    }
                    else -> {
                        Try.failure {
                            ServiceError.of(
                                """dependent data_element [ path: %s ] not mapped 
                                |to data_element source [ path: %s ] 
                                |in request graph at expected location"""
                                    .flatten(),
                                l.sourcePoint,
                                l.destinationPoint
                            )
                        }
                    }
                }
            }
            .tryReduce(
                persistentListOf(),
                PersistentList<DirectedLine<GQLOperationPath>>::add,
                PersistentList<DirectedLine<GQLOperationPath>>::addAll
            )
            .flatMap { dataElementLines: List<DirectedLine<GQLOperationPath>> ->
                when {
                    dataElementLines.isEmpty() -> {
                        Try.success<Option<DirectedLine<GQLOperationPath>>>(None)
                    }
                    dataElementLines.size > 1 -> {
                        Try.failure<Option<DirectedLine<GQLOperationPath>>> {
                            ServiceError.of(
                                """more than one data_element edge found 
                                |for connecting feature path argument [ path: %s ] 
                                |to its source [ %s ]"""
                                    .flatten(),
                                argumentPath,
                                dataElementLines.asSequence().joinToString(", ")
                            )
                        }
                    }
                    else -> {
                        Try.success(dataElementLines[0].toOption())
                    }
                }
            }
            .flatMap { dataElementLine: Option<DirectedLine<GQLOperationPath>> ->
                when (val del: DirectedLine<GQLOperationPath>? = dataElementLine.orNull()) {
                    null -> {
                        createArgumentPublisherForDependentFeature(
                            context,
                            argumentPath,
                            argumentGroupIndex
                        )
                    }
                    else -> {
                        createArgumentPublisherForDependentDataElement(context, del, argumentPath)
                    }
                }
            }
    }

    private fun createArgumentPublisherForDependentDataElement(
        context: DispatchedRequestMaterializationGraphContext,
        lineFromDataElementFieldToItsSource: DirectedLine<GQLOperationPath>,
        argumentPath: GQLOperationPath,
    ): Try<Pair<GQLOperationPath, Mono<JsonNode>>> {
        return context.dataElementPublishersByOperationPath
            .get(lineFromDataElementFieldToItsSource.destinationPoint)
            .toOption()
            .successIfDefined {
                ServiceError.of(
                    """dependent data_element source [ path: %s ] not found 
                    |in data_element_publishers_by_path; 
                    |out of order processing may have occurred"""
                        .flatten(),
                    lineFromDataElementFieldToItsSource.destinationPoint
                )
            }
            .map { dep: Mono<JsonNode> ->
                dep.flatMap { jn: JsonNode ->
                    val childPath: GQLOperationPath =
                        GQLOperationPath.of {
                            selections(
                                lineFromDataElementFieldToItsSource.sourcePoint.selection
                                    .asSequence()
                                    .drop(
                                        lineFromDataElementFieldToItsSource.destinationPoint
                                            .selection
                                            .size
                                    )
                                    .toList()
                            )
                        }
                    // TODO: Support of array indexing on subnodes necessary
                    JsonNodeValueExtractionByOperationPath.invoke(jn, childPath)
                        .successIfDefined {
                            ServiceError.of(
                                """child_path [ %s ] not found on json_node value 
                                |[ %s ] output for data_element source [ path: %s ]"""
                                    .flatten(),
                                argumentPath,
                                jn,
                                lineFromDataElementFieldToItsSource.destinationPoint
                            )
                        }
                        .toMono()
                }
            }
            .map { dep: Mono<JsonNode> -> argumentPath to dep }
    }

    private fun createArgumentPublisherForDependentFeature(
        context: DispatchedRequestMaterializationGraphContext,
        argumentPath: GQLOperationPath,
        argumentGroupIndex: Int,
    ): Try<Pair<GQLOperationPath, Mono<JsonNode>>> {
        return context.featureCalculatorPublishersByOperationPath
            .getOrNone(argumentPath)
            .filter { fps: ImmutableList<Mono<TrackableValue<JsonNode>>> ->
                // TODO: Figure out the correct index to fetch
                // from dependent
                // feature list of trackable value publishers
                argumentGroupIndex in fps.indices
            }
            .map { fps: ImmutableList<Mono<TrackableValue<JsonNode>>> ->
                fps.get(argumentGroupIndex)
            }
            .map { fp: Mono<TrackableValue<JsonNode>> ->
                fp.flatMap { tv: TrackableValue<JsonNode> ->
                    when (tv) {
                        is TrackableValue.PlannedValue<JsonNode> -> {
                            Mono.error {
                                ServiceError.of(
                                    """dependent feature value [ %s ] planned 
                                    |but not calculated or tracked"""
                                        .flatten(),
                                    tv.operationPath
                                )
                            }
                        }
                        is TrackableValue.CalculatedValue<JsonNode> -> {
                            Mono.just(tv.calculatedValue)
                        }
                        is TrackableValue.TrackedValue<JsonNode> -> {
                            Mono.just(tv.trackedValue)
                        }
                    }
                }
            }
            .map { fp: Mono<JsonNode> -> argumentPath to fp }
            .successIfDefined {
                ServiceError.of(
                    """dependent feature value [ %s ] not found 
                    |in feature_calculator_publishers_by_path map; 
                    |out of order processing may have occurred"""
                        .flatten(),
                    argumentPath
                )
            }
    }

    private fun createDispatchedRequestMaterializationGraphFromContext():
        (DispatchedRequestMaterializationGraphContext) -> DispatchedRequestMaterializationGraph {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            DefaultDispatchedRequestMaterializationGraph(
                materializedArgumentsByPath = drmgc.materializedArgumentsByPath,
                transformerPublishersByPath = drmgc.transformerPublishersByResultPath,
                dataElementPublishersByPath = drmgc.dataElementPublishersByResultPath,
                featureCalculatorPublishersByPath = drmgc.featureCalculatorPublishersByResultPath,
                passThruColumns = drmgc.passThruColumns,
            )
        }
    }

    private fun dispatchedRequestMaterializationGraphSuccessLogger():
        (GraphQLSingleRequestSession) -> Unit {
        return { _: GraphQLSingleRequestSession ->
            logger.info("${METHOD_TAG}: [ status: successful ]")
        }
    }

    private fun dispatchedRequestMaterializationGraphFailureLogger(): (Throwable) -> Unit {
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
