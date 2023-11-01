package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.json.JsonNodeToStandardValueConverter
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.ListSegment
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.schema.FieldCoordinates
import java.util.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()

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
                Mono.fromSupplier<Map<String, JsonNode>>(Collections::emptyMap).widen()
            }
            selectedFieldRefersToFeaturesElementType(session) -> {
                Mono.fromSupplier<List<JsonNode>>(Collections::emptyList).widen()
            }
            selectedFieldUnderDataElementElementType(session) -> {
                extractDataElementValueFromSource(session)
            }
            selectedFieldUnderTransformerElementType(session) -> {
                getTransformerPublisher(session, dispatchedRequestMaterializationGraph)
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
        return Flux.fromIterable(
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
            .flatMap { dep: Mono<out Pair<String, JsonNode>> -> dep }
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

    private fun getTransformerPublisher(
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
}
