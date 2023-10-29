package funcify.feature.materializer.service

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.materializer.dispatch.DispatchedRequestMaterializationGraph
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.result.GQLResultPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.schema.FieldCoordinates
import java.util.*
import org.slf4j.Logger
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
            selectedFieldRefersToDataElementOrTransformerElementType(session) -> {
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

    private fun selectedFieldRefersToDataElementOrTransformerElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return GQLResultPath.fromNativeResultPath(
                session.dataFetchingEnvironment.executionStepInfo.path
            )
            .let { rp: GQLResultPath ->
                rp.getParentPath().filter { pp: GQLResultPath -> pp.isRoot() }.isDefined() &&
                    session.fieldCoordinates
                        .filter { fc: FieldCoordinates ->
                            fc ==
                                session.materializationMetamodel.featureEngineeringModel
                                    .dataElementFieldCoordinates ||
                                fc ==
                                    session.materializationMetamodel.featureEngineeringModel
                                        .transformerFieldCoordinates
                        }
                        .isDefined()
            }
    }

    private fun selectedFieldRefersToFeaturesElementType(
        session: SingleRequestFieldMaterializationSession
    ): Boolean {
        return GQLResultPath.fromNativeResultPath(
                session.dataFetchingEnvironment.executionStepInfo.path
            )
            .let { rp: GQLResultPath ->
                rp.getParentPath().filter { pp: GQLResultPath -> pp.isRoot() }.isDefined() &&
                    session.fieldCoordinates
                        .filter { fc: FieldCoordinates ->
                            fc ==
                                session.materializationMetamodel.featureEngineeringModel
                                    .featureFieldCoordinates
                        }
                        .isDefined()
            }
    }
}
