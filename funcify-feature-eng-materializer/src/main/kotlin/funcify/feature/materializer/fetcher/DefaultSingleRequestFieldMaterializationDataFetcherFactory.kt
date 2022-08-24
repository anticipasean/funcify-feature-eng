package funcify.feature.materializer.fetcher

import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.materializer.service.SingleRequestMaterializationGraphService
import funcify.feature.materializer.service.SingleRequestMaterializationPreprocessingService
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactoryEnvironment
import graphql.schema.GraphQLNamedOutputType
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import org.slf4j.Logger

internal class DefaultSingleRequestFieldMaterializationDataFetcherFactory(
    private val asyncExecutor: Executor,
    private val singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
    private val singleRequestMaterializationPreprocessingService:
        SingleRequestMaterializationPreprocessingService
) : SingleRequestFieldMaterializationDataFetcherFactory {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestFieldMaterializationDataFetcherFactory>()
    }

    override fun get(
        environment: DataFetcherFactoryEnvironment?
    ): DataFetcher<CompletionStage<out DataFetcherResult<Any?>>> {
        val fieldTypeName: String? =
            environment
                ?.fieldDefinition
                ?.type
                .toOption()
                .filterIsInstance<GraphQLNamedOutputType>()
                .map(GraphQLNamedOutputType::getName)
                .orNull()
        logger.debug(
            """get: [ data_fetcher_factory_environment: 
            |[ graphql_field_definition: 
            |{ name: ${environment?.fieldDefinition?.name}, 
            |type: $fieldTypeName 
            |} ] ]""".flatten()
        )
        return DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher<Any?>(
            DefaultSingleRequestSessionFieldMaterializationProcessor(
                asyncExecutor = asyncExecutor,
                singleRequestMaterializationGraphService = singleRequestMaterializationGraphService,
                singleRequestMaterializationPreprocessingService =
                    singleRequestMaterializationPreprocessingService
            )
        )
    }
}
