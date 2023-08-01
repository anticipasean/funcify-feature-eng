package funcify.feature.materializer.fetcher

import funcify.feature.materializer.service.SingleRequestMaterializationOrchestratorService
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactoryEnvironment
import graphql.schema.GraphQLTypeUtil
import org.slf4j.Logger
import java.util.concurrent.CompletionStage

internal class DefaultSingleRequestFieldMaterializationDataFetcherFactory(
    private val singleRequestMaterializationOrchestratorService:
        SingleRequestMaterializationOrchestratorService
) : SingleRequestFieldMaterializationDataFetcherFactory {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestFieldMaterializationDataFetcherFactory>()
    }

    override fun get(
        environment: DataFetcherFactoryEnvironment?
    ): DataFetcher<CompletionStage<out DataFetcherResult<Any?>>> {
        logger.debug(
            """get: [ data_fetcher_factory_environment: 
            |[ graphql_field_definition: 
            |{ name: ${environment?.fieldDefinition?.name}, 
            |type: ${environment?.fieldDefinition?.type?.run { GraphQLTypeUtil.simplePrint(this) }} 
            |} ] ]"""
                .flatten()
        )
        return SingleRequestMaterializationDataFetcher<Any?>(
            singleRequestMaterializationOrchestratorService
        )
    }
}
