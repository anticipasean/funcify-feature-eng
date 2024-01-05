package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.json.GQLResultValuesToJsonNodeConverter
import funcify.feature.schema.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.PairExtensions.bimap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()

        private val snakeCaseTranslator: (String) -> String by lazy {
            val snakeCaseStrategy: SnakeCaseStrategy = SnakeCaseStrategy()
            val cache: ConcurrentMap<String, String> = ConcurrentHashMap();
            { name: String -> cache.computeIfAbsent(name, snakeCaseStrategy::translate) }
        }

        private inline fun <reified T> currentSourceValueIsInstanceOf(
            session: SingleRequestFieldMaterializationSession
        ): Boolean {
            return session.dataFetchingEnvironment.getSource<Any?>() is T
        }
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any?> {
        logger.info(
            """materialize_value_in_session: [ 
            |session_id: ${session.sessionId}, 
            |env.execution_step_info.path: ${session.dataFetchingEnvironment.executionStepInfo.path}, 
            |field.name: ${session.field.name}, 
            |field_coordinates: ${session.fieldCoordinates.orNull()}, 
            |source: ${session.dataFetchingEnvironment.getSource<Any?>()} 
            |]"""
                .flatten()
        )
        return when (
            val dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph? =
                session.singleRequestSession.dispatchedRequestMaterializationGraph.orNull()
        ) {
            null -> {
                Mono.error {
                    ServiceError.of(
                        "session.%s not defined",
                        StandardNamingConventions.SNAKE_CASE.deriveName(
                            DispatchedRequestMaterializationGraph::class.simpleName ?: "<NA>"
                        )
                    )
                }
            }
            else -> {
                materializeValueInSessionThroughDispatchedRequestMaterializationGraph(
                    session,
                    dispatchedRequestMaterializationGraph
                )
            }
        }
    }

    private fun materializeValueInSessionThroughDispatchedRequestMaterializationGraph(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
    ): Mono<Any?> {
        return when {
            selectedFieldRefersToDataElementElementType(session) -> {
                createDataElementElementTypePublisher(
                    session,
                    dispatchedRequestMaterializationGraph
                )
            }
            selectedFieldRefersToTransformerElementType(session) -> {
                Mono.fromSupplier<Map<String, JsonNode>>(::persistentMapOf).widen()
            }
            selectedFieldRefersToFeaturesElementType(session) -> {
                createFeatureElementTypePublisher(session, dispatchedRequestMaterializationGraph)
            }
            selectedFieldUnderDataElementElementType(session) -> {
                extractDataElementValueFromSource(session)
            }
            selectedFieldUnderTransformerElementType(session) -> {
                getTransformerPublisherForGQLResultPath(
                    session,
                    dispatchedRequestMaterializationGraph
                )
            }
            selectedFieldUnderFeaturesElementType(session) -> {
                Mono.error {
                    ServiceError.of(
                        "features extraction not yet supported: [ path: %s ]",
                        session.gqlResultPath
                    )
                }
            }
            else -> {
                Mono.error { ServiceError.of("not yet implemented materialization logic") }
            }
        }
    }

    private fun selectedFieldRefersToDataElementElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath
            .getParentPath()
            .filter { pp: GQLResultPath -> pp.isRoot() }
            .isDefined() &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    fc ==
                        session.materializationMetamodel.featureEngineeringModel
                            .dataElementFieldCoordinates
                }
                .isDefined()
    }

    private fun createDataElementElementTypePublisher(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph,
    ): Mono<Any?> {
        // Implicitly depends on dataelement element type being non-list type
        return Flux.mergeSequential(
                dispatchedRequestMaterializationGraph.dataElementPublishersByPath
                    .asSequence()
                    .filter { (rp: GQLResultPath, _: Mono<out JsonNode>) ->
                        rp.getParentPath()
                            .filter { pp: GQLResultPath -> pp == session.gqlResultPath }
                            .isDefined()
                    }
                    .map { (rp: GQLResultPath, dep: Mono<out JsonNode>) ->
                        rp.elementSegments.lastOrNone().filterIsInstance<NameSegment>().map {
                            ns: NameSegment ->
                            dep.map { jn: JsonNode -> ns.name to jn }
                        }
                    }
                    .flatMapOptions()
                    .asIterable()
            )
            .reduce(persistentMapOf<String, JsonNode>(), PersistentMap<String, JsonNode>::plus)
            .widen()
    }

    private fun selectedFieldRefersToTransformerElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath
            .getParentPath()
            .filter { pp: GQLResultPath -> pp.isRoot() }
            .isDefined() &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    fc ==
                        session.materializationMetamodel.featureEngineeringModel
                            .transformerFieldCoordinates
                }
                .isDefined()
    }

    private fun selectedFieldRefersToFeaturesElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath
            .getParentPath()
            .filter { pp: GQLResultPath -> pp.isRoot() }
            .isDefined() &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    fc ==
                        session.materializationMetamodel.featureEngineeringModel
                            .featureFieldCoordinates
                }
                .isDefined()
    }

    private fun createFeatureElementTypePublisher(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
    ): Mono<Any?> {
        // Assumption: All feature_calculator_publishers_by_path.values have at least one entry
        // within their List<Mono<JsonNode>>
        // Extract first value from each, assessing the max list size value in the process
        // Use maxSize value to create other feature blobs
        return Flux.fromIterable(
                dispatchedRequestMaterializationGraph.featureCalculatorPublishersByPath.asIterable()
            )
            .reduce(persistentMapOf<GQLResultPath, Mono<TrackableValue<JsonNode>>>() to 0) {
                firstFpsMaxSizePair,
                (rp: GQLResultPath, fps: List<Mono<TrackableValue<JsonNode>>>) ->
                firstFpsMaxSizePair.bimap(
                    { fps1 -> fps1.put(rp, fps.get(0)) },
                    { ms: Int -> maxOf(ms, fps.size) }
                )
            }
            .flatMap { (firstFps: Map<GQLResultPath, Mono<TrackableValue<JsonNode>>>, maxSize: Int)
                ->
                when (maxSize) {
                    0 -> {
                        Mono.empty<List<JsonNode>>()
                    }
                    1 -> {
                        convertFeaturePublishersByPathIntoJsonNodePublisher(firstFps).map(::listOf)
                    }
                    else -> {
                        (0 until maxSize)
                            .asSequence()
                            .map { i: Int ->
                                dispatchedRequestMaterializationGraph
                                    .featureCalculatorPublishersByPath
                                    .asSequence()
                                    .flatMap {
                                        (
                                            rp: GQLResultPath,
                                            fps: List<Mono<TrackableValue<JsonNode>>>) ->
                                        when {
                                            fps.getOrNull(i) != null -> {
                                                sequenceOf(rp to fps.get(i))
                                            }
                                            else -> {
                                                emptySequence()
                                            }
                                        }
                                    }
                                    .reducePairsToPersistentMap()
                            }
                            .map { fpsByPath: Map<GQLResultPath, Mono<TrackableValue<JsonNode>>> ->
                                convertFeaturePublishersByPathIntoJsonNodePublisher(fpsByPath)
                            }
                            .let { jvps: Sequence<Mono<out JsonNode>> ->
                                Flux.mergeSequential(
                                        sequenceOf(
                                                convertFeaturePublishersByPathIntoJsonNodePublisher(
                                                    firstFps
                                                )
                                            )
                                            .plus(jvps)
                                            .asIterable()
                                    )
                                    .collectList()
                            }
                    }
                }
            }
    }

    private fun convertFeaturePublishersByPathIntoJsonNodePublisher(
        featurePublishersByPath: Map<GQLResultPath, Mono<TrackableValue<JsonNode>>>
    ): Mono<out JsonNode> {
        return Flux.merge(
                featurePublishersByPath
                    .asSequence()
                    .map { (rp: GQLResultPath, fp: Mono<TrackableValue<JsonNode>>) ->
                        fp.flatMap { tv: TrackableValue<JsonNode> ->
                            when {
                                tv.isPlanned() -> {
                                    Mono.error {
                                        ServiceError.of(
                                            "feature [ path: %s ] has not been calculated",
                                            tv.operationPath
                                        )
                                    }
                                }
                                else -> {
                                    Mono.just(
                                        rp to
                                            tv.fold(
                                                { pv -> JsonNodeFactory.instance.nullNode() },
                                                { cv -> cv.calculatedValue },
                                                { tkv -> tkv.trackedValue }
                                            )
                                    )
                                }
                            }
                        }
                    }
                    .asIterable()
            )
            .reduce(
                persistentMapOf<GQLResultPath, JsonNode>(),
                PersistentMap<GQLResultPath, JsonNode>::plus
            )
            .map { resultValuesByPath: PersistentMap<GQLResultPath, JsonNode> ->
                GQLResultValuesToJsonNodeConverter.invoke(resultValuesByPath)
            }
    }

    private fun selectedFieldUnderDataElementElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath.elementSegments.size > 1 &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    session.materializationMetamodel.fieldCoordinatesAvailableUnderPath.invoke(
                        fc,
                        session.materializationMetamodel.dataElementElementTypePath
                    )
                }
                .isDefined()
    }

    private fun extractDataElementValueFromSource(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any?> {
        return when {
            currentSourceValueIsInstanceOf<Map<String, JsonNode>>(session) -> {
                session.dataFetchingEnvironment
                    .getSource<Map<String, JsonNode>>()
                    .toOption()
                    .flatMap { m: Map<String, JsonNode> ->
                        m.getOrNone(session.field.resultKey)
                            .orElse {
                                session.fieldCoordinates
                                    .filter { fc: FieldCoordinates ->
                                        fc.fieldName != session.field.resultKey
                                    }
                                    .flatMap { fc: FieldCoordinates -> m.getOrNone(fc.fieldName) }
                            }
                            .orElse {
                                session.fieldCoordinates.flatMap { fc: FieldCoordinates ->
                                    session.materializationMetamodel.aliasCoordinatesRegistry
                                        .getAllAliasesForField(fc)
                                        .asSequence()
                                        .firstOrNone { n: String -> n in m }
                                        .flatMap { n: String -> m.getOrNone(n) }
                                }
                            }
                            .orElse {
                                session.field.resultKey
                                    .toOption()
                                    .mapNotNull(snakeCaseTranslator)
                                    .flatMap { n: String -> m.getOrNone(n) }
                            }
                    }
                    .flatMap { jn: JsonNode ->
                        JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                    }
                    .toMono()
                    .widen()
            }
            currentSourceValueIsInstanceOf<List<JsonNode>>(session) -> {
                session.dataFetchingEnvironment
                    .getSource<List<JsonNode>>()
                    .toOption()
                    .flatMap { jns: List<JsonNode> ->
                        session.gqlResultPath.elementSegments
                            .lastOrNone()
                            .filterIsInstance<ListSegment>()
                            .flatMap { ls: ListSegment -> ls.indices.lastOrNone() }
                            .filter { i: Int -> i in jns.indices }
                            .mapNotNull { i: Int -> jns.get(i) }
                    }
                    .flatMap { jn: JsonNode ->
                        JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                    }
                    .toMono()
                    .widen()
            }
            currentSourceValueIsInstanceOf<JsonNode>(session) -> {
                session.dataFetchingEnvironment
                    .getSource<JsonNode>()
                    .toOption()
                    .flatMap { jn: JsonNode ->
                        JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                    }
                    .toMono()
                    .widen()
            }
            else -> {
                Mono.error {
                    ServiceError.of(
                        "could not find value for [ path: %s ] under source [ %s ]",
                        session.gqlResultPath,
                        session.dataFetchingEnvironment.getSource()
                    )
                }
            }
        }
    }

    private fun selectedFieldUnderTransformerElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath.elementSegments.size > 1 &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    session.materializationMetamodel.fieldCoordinatesAvailableUnderPath.invoke(
                        fc,
                        session.materializationMetamodel.transformerElementTypePath
                    )
                }
                .isDefined()
    }

    private fun getTransformerPublisherForGQLResultPath(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
    ): Mono<Any?> {
        return when {
            session.gqlResultPath in
                dispatchedRequestMaterializationGraph.transformerPublishersByPath -> {
                dispatchedRequestMaterializationGraph.transformerPublishersByPath
                    .getOrNone(session.gqlResultPath)
                    .map { tp: Mono<JsonNode> ->
                        tp.flatMap { jn: JsonNode ->
                            JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                                .toMono()
                        }
                    }
                    .fold(::empty, ::identity)
            }
            else -> {
                Mono.fromSupplier<Map<String, Any?>>(::persistentMapOf).widen()
            }
        }
    }

    private fun selectedFieldUnderFeaturesElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.gqlResultPath.elementSegments.size > 1 &&
            session.fieldCoordinates
                .filter { fc: FieldCoordinates ->
                    session.materializationMetamodel.fieldCoordinatesAvailableUnderPath.invoke(
                        fc,
                        session.materializationMetamodel.featureElementTypePath
                    )
                }
                .isDefined()
    }
}
