package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.loader.ReactiveBatchDataLoader
import funcify.feature.materializer.loader.ReactiveDataLoader
import funcify.feature.materializer.loader.ReactiveDataLoaderRegistry
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.json.JsonMapper
import graphql.execution.ResultPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class DefaultSingleRequestMaterializationOrchestratorService(
    private val jsonMapper: JsonMapper,
) : SingleRequestMaterializationOrchestratorService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationOrchestratorService>()

        private val resultPathToGQLOperationPathsMemoizer:
            (ResultPath) -> Pair<GQLOperationPath, GQLOperationPath> by lazy {
            val cache: ConcurrentMap<ResultPath, Pair<GQLOperationPath, GQLOperationPath>> =
                ConcurrentHashMap();
            { rp: ResultPath ->
                cache.computeIfAbsent(
                    rp,
                    resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator()
                )!!
            }
        }

        private fun resultPathToFieldSchematicPathWithAndWithoutListIndexingCalculator():
            (ResultPath) -> Pair<GQLOperationPath, GQLOperationPath> {
            return { resultPath: ResultPath ->
                GQLOperationPath.of { fields(resultPath.keysOnly) }
                    .let { pathWithoutListIndexing ->
                        resultPath
                            .toOption()
                            .mapNotNull { rp: ResultPath -> rp.toString() }
                            .map { rpStr: String ->
                                rpStr.split("/").asSequence().filter { s -> s.isNotEmpty() }
                            }
                            .map { sSeq: Sequence<String> ->
                                GQLOperationPath.of { fields(sSeq.toList()) }
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
        return when {
            currentFieldPath.level() == 1 -> {
                Mono.just(JsonNodeFactory.instance.objectNode())
            }
            currentFieldPath.level() == 2 -> {
                val loader: ReactiveDataLoader<GQLOperationPath, JsonNode> =
                    ReactiveDataLoader.newLoader(createReactiveBatchDataLoader(currentFieldPath))
                val (updatedLoader, fieldValuePublisher) = loader.loadDataForKey(currentFieldPath)
                val updatedRegistry: ReactiveDataLoaderRegistry<GQLOperationPath> =
                    session.singleRequestSession.reactiveDataLoaderRegistry.register(
                        currentFieldPath,
                        updatedLoader
                    )
                session.graphQLContext.put(
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                    session.singleRequestSession.update {
                        reactiveDataLoaderRegistry(updatedRegistry)
                    }
                )
                fieldValuePublisher.widen()
            }
            else -> {
                val domainPath: GQLOperationPath =
                    GQLOperationPath.of {
                        fields(currentFieldPath.selection.asSequence().take(2).toList())
                    }
                val loader: ReactiveDataLoader<GQLOperationPath, JsonNode> =
                    session.singleRequestSession.reactiveDataLoaderRegistry
                        .getOrNone(domainPath)
                        .filterIsInstance<ReactiveDataLoader<GQLOperationPath, JsonNode>>()
                        .successIfDefined {
                            ServiceError.of(
                                "reactive_data_loader not registered for domain path [ %s ]",
                                domainPath
                            )
                        }
                        .orElseThrow()
                val (updatedLoader, fieldValuePublisher) = loader.loadDataForKey(currentFieldPath)
                val updatedRegistry: ReactiveDataLoaderRegistry<GQLOperationPath> =
                    session.singleRequestSession.reactiveDataLoaderRegistry.register(
                        currentFieldPath,
                        updatedLoader
                    )
                session.graphQLContext.put(
                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                    session.singleRequestSession.update {
                        reactiveDataLoaderRegistry(updatedRegistry)
                    }
                )
                fieldValuePublisher.widen()
            }
        }
    }

    private fun createReactiveBatchDataLoader(
        path: GQLOperationPath
    ): ReactiveBatchDataLoader<GQLOperationPath, JsonNode> {
        return ReactiveBatchDataLoader<GQLOperationPath, JsonNode> {
            arguments: ImmutableMap<GQLOperationPath, JsonNode>,
            outputKeys: ImmutableSet<GQLOperationPath> ->
            logger.info("load: [ path: {} ]", path)
            Mono.delay(Duration.ofMillis(500))
                .then(
                    Mono.just(
                        outputKeys
                            .stream()
                            .map { p: GQLOperationPath ->
                                p to JsonNodeFactory.instance.textNode(p.toString())
                            }
                            .reducePairsToPersistentMap()
                    )
                )
        }
    }

    private fun getFieldSchematicPathWithAndWithoutListIndexing(
        session: SingleRequestFieldMaterializationSession
    ): Pair<GQLOperationPath, GQLOperationPath> {
        return resultPathToGQLOperationPathsMemoizer(
            session.dataFetchingEnvironment.executionStepInfo.path
        )
    }
}
