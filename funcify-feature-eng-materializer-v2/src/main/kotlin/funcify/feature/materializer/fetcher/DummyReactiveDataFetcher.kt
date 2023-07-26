package funcify.feature.materializer.fetcher

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import graphql.schema.DataFetchingEnvironment
import kotlinx.collections.immutable.ImmutableList
import org.dataloader.DataLoader
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-23
 */
class DummyReactiveDataFetcher : ReactiveDataFetcher<Any> {

    companion object {
        private val logger: Logger = loggerFor<DummyReactiveDataFetcher>()
    }
    override fun invoke(environment: DataFetchingEnvironment): Mono<Any> {
        val sp: SchematicPath =
            SchematicPath.of { pathSegments(environment.executionStepInfo.path.keysOnly) }
        logger.info(
            "invoke: [ path: {}, environment.field.name: {}, environment.execution_step_info.result_key: {} ]",
            sp,
            environment.field.name,
            environment.executionStepInfo.resultKey
        )
        return when {
            sp.pathSegments.size > 1 -> {
                val domainSegment: String =
                    sp.pathSegments
                        .toOption()
                        .filter { ps: ImmutableList<String> -> ps.size >= 2 }
                        .map { ps: ImmutableList<String> -> ps[1] }
                        .getOrElse { environment.executionStepInfo.resultKey }
                when (
                    val dataLoader: DataLoader<SchematicPath, JsonNode>? =
                        environment.getDataLoader<SchematicPath, JsonNode>(domainSegment)
                ) {
                    null -> {
                        Mono.error {
                            ServiceError.of(
                                "no data_loader available for domain_path_segment: [ name: %s ]",
                                domainSegment
                            )
                        }
                    }
                    else -> {
                        logger.info("data_loader_statistics: {}", dataLoader.statistics)
                        Mono.fromFuture<JsonNode>(dataLoader.load(sp, environment))
                    }
                }
            }
            else -> {
                logger.info("returning source for [ path: {} ]", sp)
                Mono.just(environment.getSource<JsonNode>())
            }
        }.widen()
    }
}
