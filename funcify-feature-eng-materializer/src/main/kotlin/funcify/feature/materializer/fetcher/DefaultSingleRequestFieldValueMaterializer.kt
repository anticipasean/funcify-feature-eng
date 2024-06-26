package funcify.feature.materializer.fetcher

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.output.SingleRequestJsonFieldValueDeserializer
import funcify.feature.materializer.session.field.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.result.ElementSegment
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnmodifiedType
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class DefaultSingleRequestFieldValueMaterializer(
    private val jsonMapper: JsonMapper,
    private val singleRequestJsonFieldValueDeserializer: SingleRequestJsonFieldValueDeserializer,
) : SingleRequestFieldValueMaterializer {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestFieldValueMaterializer>()

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

    override fun materializeValueForFieldInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any?> {
        logger.info(
            """materialize_value_in_session: [ 
            |session_id: ${session.sessionId}, 
            |env.execution_step_info.path: ${session.dataFetchingEnvironment.executionStepInfo.path}, 
            |field.name: ${session.field.name}, 
            |field_coordinates: ${session.fieldCoordinates.orNull()}, 
            |source: ${session.dataFetchingEnvironment.getSource<Any?>()}, 
            |source.type: ${session.dataFetchingEnvironment.getSource<Any?>()?.run { this::class.simpleName }} 
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
                extractFeatureValueFromSource(session, dispatchedRequestMaterializationGraph)
            }
            else -> {
                Mono.error { ServiceError.of("not yet implemented materialization logic") }
            }
        }
    }

    private fun selectedFieldRefersToDataElementElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.fieldCoordinates.exists { fc: FieldCoordinates ->
            session.materializationMetamodel.featureEngineeringModel.dataElementFieldCoordinates ==
                fc
        }
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
        return session.fieldCoordinates.exists { fc: FieldCoordinates ->
            session.materializationMetamodel.featureEngineeringModel.transformerFieldCoordinates ==
                fc
        }
    }

    private fun selectedFieldRefersToFeaturesElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return session.fieldCoordinates.exists { fc: FieldCoordinates ->
            session.materializationMetamodel.featureEngineeringModel.featureFieldCoordinates == fc
        }
    }

    private fun createFeatureElementTypePublisher(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
    ): Mono<Any?> {
        return dispatchedRequestMaterializationGraph.featureCalculatorPublishersByPath.keys
            .asSequence()
            .flatMap { rp: GQLResultPath -> rp.elementSegments.firstOrNone().sequence() }
            .toSet()
            .let { numberOfUniqueTopFeatureNodes: Set<ElementSegment> ->
                when (numberOfUniqueTopFeatureNodes.size) {
                    0 -> {
                        Mono.fromSupplier<List<Map<String, JsonNode>>>(::persistentListOf).widen()
                    }
                    else -> {
                        Mono.fromSupplier {
                            numberOfUniqueTopFeatureNodes.indices.fold(
                                persistentListOf<Map<String, JsonNode>>()
                            ) { pl: PersistentList<Map<String, JsonNode>>, _: Int ->
                                pl.add(persistentMapOf<String, JsonNode>())
                            }
                        }
                    }
                }
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
            session.fieldCoordinates.exists { fc: FieldCoordinates ->
                session.materializationMetamodel.domainSpecifiedDataElementSourceByCoordinates
                    .containsKey(fc)
            } && currentSourceValueIsInstanceOf<Map<String, JsonNode>>(session) -> {
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
                    .map { jn: JsonNode ->
                        jn.toOption()
                            .filterIsInstance<ObjectNode>()
                            .filter { on: ObjectNode ->
                                session.fieldCoordinates.exists { fc: FieldCoordinates ->
                                    on.has(fc.fieldName) && on.size() == 1
                                }
                            }
                            .flatMap { on: ObjectNode ->
                                session.fieldCoordinates.mapNotNull { fc: FieldCoordinates ->
                                    on.get(fc.fieldName)
                                }
                            }
                            .getOrElse { jn }
                    }
                    .flatMap { jn: JsonNode ->
                        singleRequestJsonFieldValueDeserializer
                            .deserializeValueForFieldFromJsonInSession(session, jn)
                    }
                    .toMono()
                    .widen()
            }
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
                        singleRequestJsonFieldValueDeserializer
                            .deserializeValueForFieldFromJsonInSession(session, jn)
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
                        singleRequestJsonFieldValueDeserializer
                            .deserializeValueForFieldFromJsonInSession(session, jn)
                    }
                    .toMono()
                    .widen()
            }
            currentSourceValueIsInstanceOf<JsonNode>(session) -> {
                session.dataFetchingEnvironment
                    .getSource<JsonNode>()
                    .toOption()
                    .filterIsInstance<ObjectNode>()
                    .flatMap { on: ObjectNode ->
                        when {
                            on.has(session.field.name) -> {
                                on.get(session.field.name).toOption()
                            }
                            session.field.name != session.field.resultKey &&
                                on.has(session.field.resultKey) -> {
                                on.get(session.field.resultKey).toOption()
                            }
                            session.fieldCoordinates.exists { fc: FieldCoordinates ->
                                session.materializationMetamodel.aliasCoordinatesRegistry
                                    .getAllAliasesForField(fc)
                                    .asSequence()
                                    .any { alias: String -> on.has(alias) }
                            } -> {
                                session.fieldCoordinates.flatMap { fc: FieldCoordinates ->
                                    session.materializationMetamodel.aliasCoordinatesRegistry
                                        .getAllAliasesForField(fc)
                                        .asSequence()
                                        .firstOrNone() { alias: String -> on.has(alias) }
                                        .mapNotNull { alias: String -> on.get(alias) }
                                }
                            }
                            session.field.resultKey
                                .toOption()
                                .mapNotNull(snakeCaseTranslator)
                                .exists { n: String -> on.has(n) } -> {
                                session.field.resultKey
                                    .toOption()
                                    .mapNotNull(snakeCaseTranslator)
                                    .mapNotNull { n: String -> on.get(n) }
                            }
                            session.fieldOutputType
                                .toOption()
                                .map(GraphQLTypeUtil::unwrapAll)
                                .exists { gut: GraphQLUnmodifiedType ->
                                    gut is GraphQLImplementingType
                                } -> {
                                on.some()
                            }
                            else -> {
                                none()
                            }
                        }
                    }
                    .flatMap { jn: JsonNode ->
                        singleRequestJsonFieldValueDeserializer
                            .deserializeValueForFieldFromJsonInSession(session, jn)
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
            dispatchedRequestMaterializationGraph.transformerPublishersByPath.containsKey(
                session.gqlResultPath
            ) -> {
                dispatchedRequestMaterializationGraph.transformerPublishersByPath
                    .getOrNone(session.gqlResultPath)
                    .map { tp: Mono<out JsonNode> ->
                        tp.flatMap { jn: JsonNode ->
                            singleRequestJsonFieldValueDeserializer
                                .deserializeValueForFieldFromJsonInSession(session, jn)
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

    private fun extractFeatureValueFromSource(
        session: SingleRequestFieldMaterializationSession,
        dispatchedRequestMaterializationGraph: DispatchedRequestMaterializationGraph
    ): Mono<Any?> {
        return when {
            // Case 1: Value is a container: an empty map is a signal value
            session.fieldOutputType.toOption().mapNotNull(GraphQLTypeUtil::unwrapAll).exists {
                gut: GraphQLUnmodifiedType ->
                GraphQLTypeUtil.isObjectType(gut) || GraphQLTypeUtil.isInterfaceOrUnion(gut)
            } -> {
                Mono.fromSupplier<Map<String, JsonNode>>(::persistentMapOf).widen()
            }
            // Case 2: Value has a feature calculator publisher associated
            dispatchedRequestMaterializationGraph.featureCalculatorPublishersByPath.containsKey(
                session.gqlResultPath
            ) -> {
                dispatchedRequestMaterializationGraph.featureCalculatorPublishersByPath
                    .getOrNone(session.gqlResultPath)
                    .map { tvp: Mono<out TrackableValue<JsonNode>> ->
                        tvp.flatMap { tv: TrackableValue<JsonNode> ->
                                when {
                                    tv.isPlanned() -> {
                                        Mono.error {
                                            ServiceError.of(
                                                "feature [ path: %s ] has not been calculated",
                                                session.gqlResultPath
                                            )
                                        }
                                    }
                                    else -> {
                                        Mono.just(
                                            tv.fold(
                                                { pv -> JsonNodeFactory.instance.nullNode() },
                                                { cv -> cv.calculatedValue },
                                                { tkv -> tkv.trackedValue }
                                            )
                                        )
                                    }
                                }
                            }
                            .flatMap { jn: JsonNode ->
                                singleRequestJsonFieldValueDeserializer
                                    .deserializeValueForFieldFromJsonInSession(session, jn)
                                    .toMono()
                            }
                            .cache()
                    }
                    .fold(::empty, ::identity)
            }
            else -> {
                Mono.error {
                    ServiceError.of(
                        "no feature value publisher has been created for [ path: %s ]",
                        session.gqlResultPath
                    )
                }
            }
        }
    }
}
