package funcify.feature.materializer.dispatch

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
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
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StreamExtensions.asIterable
import funcify.feature.tools.extensions.StreamExtensions.recurse
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.json.JsonMapper
import funcify.feature.tools.json.MappingTarget.Companion.toKotlinObject
import graphql.language.VariableReference
import java.time.Duration
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultSingleRequestMaterializationDispatchService(
    private val jsonMapper: JsonMapper,
    private val dispatchedRequestMaterializationGraphContextFactory:
        DispatchedRequestMaterializationGraphContextFactory =
        DefaultDispatchedRequestMaterializationGraphContextFactory()
) : SingleRequestMaterializationDispatchService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationDispatchService>()
        private const val DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS: Int = 4
        private val DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION: Duration =
            Duration.ofSeconds(DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS.toLong())
    }

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            "dispatch_requests_in_materialization_graph_in_session: [ session.session_id: ${session.sessionId} ]"
        )
        return when (
            val requestMaterializationGraph: RequestMaterializationGraph? =
                session.requestMaterializationGraph.orNull()
        ) {
            null -> {
                Mono.error<GraphQLSingleRequestSession> {
                    ServiceError.of(
                        "request_materialization_graph has not been defined in session [ session.session_id: {} ]",
                        session.sessionId
                    )
                }
            }
            else -> {
                dispatchRequestsForCallablesInMaterializationGraph(
                    session,
                    requestMaterializationGraph
                )
            }
        }
    }

    private fun dispatchRequestsForCallablesInMaterializationGraph(
        session: GraphQLSingleRequestSession,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Mono<GraphQLSingleRequestSession> {
        return createDispatchedRequestMaterializationGraph(session, requestMaterializationGraph)
            .map { drmg: DispatchedRequestMaterializationGraph ->
                session.update { dispatchedRequestMaterializationGraph(drmg) }
            }
    }

    private fun createDispatchedRequestMaterializationGraph(
        session: GraphQLSingleRequestSession,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Mono<DispatchedRequestMaterializationGraph> {
        return Mono.fromCallable {
                when (val ric: RawInputContext? = session.rawInputContext.orNull()) {
                    null -> {
                        dispatchedRequestMaterializationGraphContextFactory
                            .builder()
                            .requestMaterializationGraph(requestMaterializationGraph)
                            .variables(extractVariablesFromSession(session).orElseThrow())
                            .build()
                    }
                    else -> {
                        dispatchedRequestMaterializationGraphContextFactory
                            .builder()
                            .requestMaterializationGraph(requestMaterializationGraph)
                            .rawInputContext(ric)
                            .variables(extractVariablesFromSession(session).orElseThrow())
                            .build()
                    }
                }
            }
            .flatMap(dispatchAllTransformerCallablesWithinContext())
            .flatMap(dispatchAllDataElementCallablesWithinContext())
            .flatMap(dispatchAllFeatureCalculatorCallablesWithinContext())
            .map(createDispatchedRequestMaterializationGraphFromContext())
    }

    private fun extractVariablesFromSession(
        session: GraphQLSingleRequestSession
    ): Try<Map<String, JsonNode>> {
        // TODO: Consider whether these must first be converted into GraphQL Value<*>s via
        // CoercedVariables mechanism before serialization into JSON
        return jsonMapper
            .fromKotlinObject<Map<String, Any?>>(session.rawGraphQLRequest.variables)
            .toKotlinObject<Map<String, JsonNode>>()
            .mapFailure { t: Throwable ->
                ServiceError.of(
                    "unable to successfully convert variables into json values map [ message: %s ]",
                    t.message
                )
            }
    }

    private fun dispatchAllTransformerCallablesWithinContext():
        (DispatchedRequestMaterializationGraphContext) -> Mono<
                DispatchedRequestMaterializationGraphContext
            > {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.transformerCallablesByPath.asIterable()
                )
                .map { (p: GQLOperationPath, tc: TransformerCallable) ->
                    p to
                        dispatchTransformerCallable(
                            drmgc.requestMaterializationGraph,
                            drmgc.variables,
                            p,
                            tc
                        )
                }
                .reduceWith(
                    ::persistentMapOf,
                    PersistentMap<GQLOperationPath, Mono<JsonNode>>::plus
                )
                .map { m: PersistentMap<GQLOperationPath, Mono<JsonNode>> ->
                    drmgc.update { addAllTransformerPublishers(m) }
                }
        }
    }

    private fun dispatchTransformerCallable(
        requestMaterializationGraph: RequestMaterializationGraph,
        variables: ImmutableMap<String, JsonNode>,
        path: GQLOperationPath,
        transformerCallable: TransformerCallable
    ): Mono<JsonNode> {
        return when (path) {
            !in requestMaterializationGraph.requestGraph -> {
                throw ServiceError.of(
                    "unable to dispatch transformer_callable for [ path: %s ]",
                    path
                )
            }
            else -> {
                Flux.merge(
                        requestMaterializationGraph.requestGraph
                            .edgesFromPointAsStream(path)
                            .map { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                                if (!l.destinationPoint.referentOnArgument()) {
                                    Mono.error<Pair<String, JsonNode>> {
                                        ServiceError.of(
                                            "dependent [ %s ] for transformer_source [ path: %s ] is not an argument",
                                            l.destinationPoint,
                                            path
                                        )
                                    }
                                } else {
                                    when (e) {
                                        MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                                            requestMaterializationGraph.requestGraph
                                                .get(l.destinationPoint)
                                                .toOption()
                                                .filterIsInstance<FieldArgumentComponentContext>()
                                                .flatMap { facc: FieldArgumentComponentContext ->
                                                    GraphQLValueToJsonNodeConverter.invoke(
                                                            facc.argument.value
                                                        )
                                                        .map { jn: JsonNode ->
                                                            facc.argument.name to jn
                                                        }
                                                }
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        "unable to extract default argument.value as json for argument [ path: %s ]",
                                                        l.destinationPoint
                                                    )
                                                }
                                                .toMono()
                                        }
                                        MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                                            requestMaterializationGraph.requestGraph
                                                .get(l.destinationPoint)
                                                .toOption()
                                                .filterIsInstance<FieldArgumentComponentContext>()
                                                .flatMap { facc: FieldArgumentComponentContext ->
                                                    facc.argument.value
                                                        .toOption()
                                                        .filterIsInstance<VariableReference>()
                                                        .map { vr: VariableReference -> vr.name }
                                                        .flatMap { n: String ->
                                                            variables.getOrNone(n)
                                                        }
                                                        .map { jn: JsonNode ->
                                                            facc.argument.name to jn
                                                        }
                                                }
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        "unable to extract argument value for [ path: %s ]",
                                                        l.destinationPoint
                                                    )
                                                }
                                                .toMono()
                                        }
                                        else -> {
                                            Mono.error<Pair<String, JsonNode>> {
                                                ServiceError.of(
                                                    "strategy for determining argument value for [ path: %s ] not available",
                                                    path
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            .asIterable()
                    )
                    .reduceWith(::persistentMapOf, PersistentMap<String, JsonNode>::plus)
                    .flatMap { m: PersistentMap<String, JsonNode> -> transformerCallable.invoke(m) }
                    .cache()
            }
        }
    }

    private fun dispatchAllDataElementCallablesWithinContext():
        (DispatchedRequestMaterializationGraphContext) -> Mono<
                DispatchedRequestMaterializationGraphContext
            > {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.dataElementCallablesByPath.asIterable()
                )
                .map { (p: GQLOperationPath, dec: DataElementCallable) ->
                    p to
                        dispatchDataElementCallable(
                            drmgc.requestMaterializationGraph,
                            drmgc.variables,
                            drmgc.rawInputContext,
                            p,
                            dec
                        )
                }
                .reduceWith(
                    ::persistentMapOf,
                    PersistentMap<GQLOperationPath, Mono<JsonNode>>::plus
                )
                .map { m: PersistentMap<GQLOperationPath, Mono<JsonNode>> ->
                    drmgc.update { addAllDataElementPublishers(m) }
                }
        }
    }

    private fun dispatchDataElementCallable(
        requestMaterializationGraph: RequestMaterializationGraph,
        variables: ImmutableMap<String, JsonNode>,
        rawInputContext: Option<RawInputContext>,
        path: GQLOperationPath,
        dataElementCallable: DataElementCallable
    ): Mono<JsonNode> {
        return when (path) {
            !in requestMaterializationGraph.requestGraph -> {
                throw ServiceError.of(
                    "unable to dispatch data_element_callable for [ path: %s ]",
                    path
                )
            }
            else -> {
                Flux.merge(
                        requestMaterializationGraph.requestGraph
                            .edgesFromPointAsStream(path)
                            .map { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                                if (!l.destinationPoint.referentOnArgument()) {
                                    Mono.error<Pair<GQLOperationPath, JsonNode>> {
                                        ServiceError.of(
                                            "dependent [ %s ] for data_element_source [ path: %s ] is not an argument",
                                            l.destinationPoint,
                                            path
                                        )
                                    }
                                } else {
                                    when (e) {
                                        MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                                            requestMaterializationGraph.requestGraph
                                                .get(l.destinationPoint)
                                                .toOption()
                                                .filterIsInstance<FieldArgumentComponentContext>()
                                                .flatMap { facc: FieldArgumentComponentContext ->
                                                    GraphQLValueToJsonNodeConverter.invoke(
                                                        facc.argument.value
                                                    )
                                                }
                                                .map { jn: JsonNode -> l.destinationPoint to jn }
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        "unable to extract default argument.value as json for argument [ path: %s ]",
                                                        l.destinationPoint
                                                    )
                                                }
                                                .toMono()
                                        }
                                        MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                                            requestMaterializationGraph.requestGraph
                                                .get(l.destinationPoint)
                                                .toOption()
                                                .filterIsInstance<FieldArgumentComponentContext>()
                                                .map { facc: FieldArgumentComponentContext ->
                                                    facc.argument.value
                                                }
                                                .filterIsInstance<VariableReference>()
                                                .map { vr: VariableReference -> vr.name }
                                                .flatMap { n: String -> variables.getOrNone(n) }
                                                .map { jn: JsonNode -> l.destinationPoint to jn }
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        "unable to extract argument value for [ path: %s ]",
                                                        l.destinationPoint
                                                    )
                                                }
                                                .toMono()
                                        }
                                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                                            requestMaterializationGraph.requestGraph
                                                .get(path)
                                                .toOption()
                                                .filterIsInstance<SelectedFieldComponentContext>()
                                                .zip(rawInputContext)
                                                .flatMap {
                                                    (
                                                        sfcc: SelectedFieldComponentContext,
                                                        ric: RawInputContext) ->
                                                    ric.get(sfcc.fieldCoordinates.fieldName)
                                                }
                                                .map { jn: JsonNode -> path to jn }
                                                .successIfDefined {
                                                    ServiceError.of(
                                                        "raw_input_context does not contain value for key [ field_name: %s ]",
                                                        requestMaterializationGraph.requestGraph
                                                            .get(path)
                                                            .toOption()
                                                            .filterIsInstance<
                                                                SelectedFieldComponentContext
                                                            >()
                                                            .map {
                                                                sfcc: SelectedFieldComponentContext
                                                                ->
                                                                sfcc.fieldCoordinates.fieldName
                                                            }
                                                            .getOrElse { "<NA>" }
                                                    )
                                                }
                                                .toMono()
                                        }
                                        else -> {
                                            Mono.error<Pair<GQLOperationPath, JsonNode>> {
                                                ServiceError.of(
                                                    "strategy for determining argument value for [ path: %s ] not available",
                                                    path
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            .asIterable()
                    )
                    .reduceWith(::persistentMapOf, PersistentMap<GQLOperationPath, JsonNode>::plus)
                    .flatMap { m: PersistentMap<GQLOperationPath, JsonNode> ->
                        dataElementCallable.invoke(m)
                    }
                    .cache()
            }
        }
    }

    private fun dispatchAllFeatureCalculatorCallablesWithinContext():
        (DispatchedRequestMaterializationGraphContext) -> Mono<
                DispatchedRequestMaterializationGraphContext
            > {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            Flux.fromIterable(
                    drmgc.requestMaterializationGraph.featureCalculatorCallablesByPath.asIterable()
                )
                .sort(bringForwardIndependentFeatureCalculationsComparator(drmgc))
                .reduceWith(
                    ::persistentMapOf,
                    createTrackedFeatureValueAndDispatchFeatureCalculatorCallable(drmgc)
                )
                .map { m: PersistentMap<GQLOperationPath, Mono<TrackableValue<JsonNode>>> ->
                    drmgc.update { addAllFeatureCalculatorPublishers(m) }
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
                    .recurse { p: GQLOperationPath ->
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
                    .recurse { p: GQLOperationPath ->
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

    private fun <M, E> createTrackedFeatureValueAndDispatchFeatureCalculatorCallable(
        drmgc: DispatchedRequestMaterializationGraphContext
    ): (M, E) -> PersistentMap<GQLOperationPath, Mono<TrackableValue<JsonNode>>> where
    M : PersistentMap<GQLOperationPath, Mono<TrackableValue<JsonNode>>>,
    E : Map.Entry<GQLOperationPath, FeatureCalculatorCallable> {
        return { m: M, (p: GQLOperationPath, fcc: FeatureCalculatorCallable) ->
            createTrackableJsonValueForFeature(drmgc, p, fcc)
                .map { tv: TrackableValue<JsonNode> ->
                    dispatchFeatureCalculatorCallable(drmgc, tv, fcc, m)
                }
                .map { tvPub: Mono<TrackableValue<JsonNode>> -> m.put(p, tvPub) }
                .orElseThrow()
        }
    }

    private fun createTrackableJsonValueForFeature(
        context: DispatchedRequestMaterializationGraphContext,
        path: GQLOperationPath,
        featureCalculatorCallable: FeatureCalculatorCallable
    ): Try<TrackableValue<JsonNode>> {
        // TODO: Make this calculation a one time operation since its output will be the same for
        // the same graph
        Stream.of(path)
            .recurse { p: GQLOperationPath ->
                context.requestMaterializationGraph.requestGraph
                    .edgesFromPointAsStream(p)
                    .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        when (e) {
                            MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED,
                            MaterializationEdge.VARIABLE_VALUE_PROVIDED,
                            MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                                Stream.of((l to e).right())
                            }
                            MaterializationEdge.EXTRACT_FROM_SOURCE -> {
                                Stream.of(l.destinationPoint.left())
                            }
                            else -> {
                                Stream.empty()
                            }
                        }
                    }
            }
            .map { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                // TODO: Add materialized raw_input or variable values to context
                // so that same value may be extracted here from context
                when (e) {
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED -> {
                        TODO()
                    }
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED -> {
                        TODO()
                    }
                    MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                        TODO()
                    }
                    else -> {}
                }
            }
        TODO()
    }

    private fun dispatchFeatureCalculatorCallable(
        context: DispatchedRequestMaterializationGraphContext,
        trackableValue: TrackableValue<JsonNode>,
        featureCalculatorCallable: FeatureCalculatorCallable,
        alreadyDispatchedFeatures: ImmutableMap<GQLOperationPath, Mono<TrackableValue<JsonNode>>>
    ): Mono<TrackableValue<JsonNode>> {
        TODO("Not yet implemented")
    }

    private fun createDispatchedRequestMaterializationGraphFromContext():
        (DispatchedRequestMaterializationGraphContext) -> DispatchedRequestMaterializationGraph {
        return { drmgc: DispatchedRequestMaterializationGraphContext ->
            TODO("not yet implemented")
        }
    }
}
