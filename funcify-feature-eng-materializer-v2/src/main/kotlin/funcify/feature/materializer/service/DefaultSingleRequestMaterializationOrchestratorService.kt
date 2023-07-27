package funcify.feature.materializer.service

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.execution.ResultPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()

        private val resultPathToSchematicPathsMemoizer:
            (ResultPath) -> Pair<SchematicPath, SchematicPath> by lazy {
            val cache: ConcurrentMap<ResultPath, Pair<SchematicPath, SchematicPath>> =
                ConcurrentHashMap();
            { rp: ResultPath ->
                cache.computeIfAbsent(
                    rp,
                    resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator()
                )!!
            }
        }

        private fun resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator():
            (ResultPath) -> Pair<SchematicPath, SchematicPath> {
            return { resultPath: ResultPath ->
                SchematicPath.of { pathSegments(resultPath.keysOnly) }
                    .let { pathWithoutListIndexing ->
                        resultPath
                            .toOption()
                            .mapNotNull { rp: ResultPath -> rp.toString() }
                            .map { rpStr: String ->
                                rpStr.split("/").asSequence().filter { s -> s.isNotEmpty() }
                            }
                            .map { sSeq: Sequence<String> ->
                                SchematicPath.of { pathSegments(sSeq.toList()) }
                            }
                            .getOrElse { pathWithoutListIndexing } to pathWithoutListIndexing
                    }
            }
        }

        private inline fun <reified T> currentSourceValueIsInstanceOf(
            session: SingleRequestFieldMaterializationSession
        ): Boolean {
            return session.dataFetchingEnvironment.getSource<Any?>() is T
        }
    }

    override fun materializeValueInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any> {
        val (currentFieldPath, currentFieldPathWithoutListIndexing) =
            getFieldSchematicPathWithAndWithoutListIndexing(session)
        logger.info(
            """materialize_value_in_session: [ 
            |session_id: ${session.sessionId}, 
            |field.name: ${session.dataFetchingEnvironment.field.name}, 
            |current_field_path: ${currentFieldPath}, 
            |current_field_path_without_list_indexing: ${currentFieldPathWithoutListIndexing} 
            |]"""
                .flatten()
        )
        return Mono.empty()
    }

    private fun getFieldSchematicPathWithAndWithoutListIndexing(
        session: SingleRequestFieldMaterializationSession
    ): Pair<SchematicPath, SchematicPath> {
        return resultPathToSchematicPathsMemoizer(
            session.dataFetchingEnvironment.executionStepInfo.path
        )
    }
}
