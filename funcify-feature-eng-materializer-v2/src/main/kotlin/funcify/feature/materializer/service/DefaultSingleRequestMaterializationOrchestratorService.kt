package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.schema.path.result.NameSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
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
}
