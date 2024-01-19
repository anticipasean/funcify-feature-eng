package funcify.feature.materializer.fetcher

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.session.field.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.json.GQLResultValuesToJsonNodeConverter
import funcify.feature.schema.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.result.ElementSegment
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLImplementingType
import graphql.schema.GraphQLTypeUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

internal class DefaultSingleRequestFieldValueMaterializer(
    private val jsonMapper: JsonMapper,
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
                extractFeatureValueFromSource(session)
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
        return Flux.merge(
                dispatchedRequestMaterializationGraph.featureCalculatorPublishersByPath
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
            .flatMap { jn: JsonNode ->
                when (jn.nodeType) {
                    JsonNodeType.OBJECT -> {
                        Mono.fromSupplier {
                            jn.get(
                                    session.materializationMetamodel.featureEngineeringModel
                                        .featureFieldCoordinates
                                        .fieldName
                                )
                                ?.toPersistentList() ?: persistentListOf<JsonNode>()
                        }
                    }
                    else -> {
                        Mono.error {
                            ServiceError.of(
                                """unable to create array of materialized features 
                                    |for expected results: [ %s ]"""
                                    .flatten(),
                                dispatchedRequestMaterializationGraph
                                    .featureCalculatorPublishersByPath
                                    .keys
                                    .asSequence()
                                    .sorted()
                                    .joinToString(", ", "{ ", " }")
                            )
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
                    .filterIsInstance<ObjectNode>()
                    .flatMap { on: ObjectNode ->
                        when {
                            session.field.selectionSet
                                .toOption()
                                .mapNotNull(SelectionSet::getSelections)
                                .filter(List<Selection<*>>::isNotEmpty)
                                .isDefined() ||
                                GraphQLTypeUtil.unwrapAll(session.fieldOutputType)
                                    .toOption()
                                    .filterIsInstance<GraphQLImplementingType>()
                                    .isDefined() -> {
                                JsonNodeToStandardValueConverter.invoke(on, session.fieldOutputType)
                                    .filterIsInstance<Map<String, JsonNode>>()
                                    .flatMap { m: Map<String, JsonNode> ->
                                        m.getOrNone(session.field.resultKey)
                                            .orElse {
                                                session.fieldCoordinates
                                                    .filter { fc: FieldCoordinates ->
                                                        fc.fieldName != session.field.resultKey
                                                    }
                                                    .flatMap { fc: FieldCoordinates ->
                                                        m.getOrNone(fc.fieldName)
                                                    }
                                            }
                                            .orElse {
                                                session.fieldCoordinates.flatMap {
                                                    fc: FieldCoordinates ->
                                                    session.materializationMetamodel
                                                        .aliasCoordinatesRegistry
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
                            }
                            else -> {
                                JsonNodeToStandardValueConverter.invoke(on, session.fieldOutputType)
                            }
                        }
                    }
                    .orElse {
                        session.dataFetchingEnvironment
                            .getSource<JsonNode>()
                            .toOption()
                            .filterNot { jn: JsonNode -> jn is ObjectNode }
                            .flatMap { jn: JsonNode ->
                                JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                            }
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

    private fun extractFeatureValueFromSource(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any?> {
        return when {
            currentSourceValueIsInstanceOf<Map<String, JsonNode>>(session) -> {
                session.dataFetchingEnvironment
                    .getSource<Map<String, JsonNode>>()
                    .toOption()
                    .flatMap { m: Map<String, JsonNode> ->
                        session.gqlResultPath.elementSegments
                            .lastOrNone()
                            .mapNotNull { es: ElementSegment ->
                                when (es) {
                                    is ListSegment -> es.name
                                    is NameSegment -> es.name
                                }
                            }
                            .flatMap(m::getOrNone)
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
                    .filterIsInstance<ObjectNode>()
                    .flatMap { on: ObjectNode ->
                        when {
                            session.field.selectionSet
                                .toOption()
                                .mapNotNull(SelectionSet::getSelections)
                                .filter(List<Selection<*>>::isNotEmpty)
                                .isDefined() ||
                                GraphQLTypeUtil.unwrapAll(session.fieldOutputType)
                                    .toOption()
                                    .filterIsInstance<GraphQLImplementingType>()
                                    .isDefined() -> {
                                session.gqlResultPath.elementSegments
                                    .lastOrNone()
                                    .map { es: ElementSegment ->
                                        when (es) {
                                            is ListSegment -> es.name
                                            is NameSegment -> es.name
                                        }
                                    }
                                    .mapNotNull(on::get)
                                    .flatMap { jn: JsonNode ->
                                        JsonNodeToStandardValueConverter.invoke(
                                            jn,
                                            session.fieldOutputType
                                        )
                                    }
                            }
                            else -> {
                                JsonNodeToStandardValueConverter.invoke(on, session.fieldOutputType)
                            }
                        }
                    }
                    .orElse {
                        session.dataFetchingEnvironment
                            .getSource<JsonNode>()
                            .toOption()
                            .filterNot { jn: JsonNode -> jn is ObjectNode }
                            .flatMap { jn: JsonNode ->
                                JsonNodeToStandardValueConverter.invoke(jn, session.fieldOutputType)
                            }
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
}
