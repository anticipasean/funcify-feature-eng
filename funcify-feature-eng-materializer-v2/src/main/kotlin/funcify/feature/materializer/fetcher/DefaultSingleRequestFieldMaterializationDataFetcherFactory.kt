package funcify.feature.materializer.fetcher

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactoryEnvironment
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeUtil
import java.util.concurrent.CompletionStage
import org.slf4j.Logger

internal class DefaultSingleRequestFieldMaterializationDataFetcherFactory(
    private val reactiveDataFetcher: ReactiveDataFetcher<Any>
) : SingleRequestFieldMaterializationDataFetcherFactory {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestFieldMaterializationDataFetcherFactory>()
    }

    override fun get(
        environment: DataFetcherFactoryEnvironment?
    ): DataFetcher<CompletionStage<out DataFetcherResult<Any?>>> {
        val fieldTypeName: String? =
            environment?.fieldDefinition?.type?.let { got: GraphQLOutputType ->
                GraphQLTypeUtil.simplePrint(got)
            }
        logger.debug(
            """get: [ data_fetcher_factory_environment: 
            |[ graphql_field_definition: 
            |{ name: ${environment?.fieldDefinition?.name}, 
            |type: $fieldTypeName 
            |} ] ]"""
                .flatten()
        )
        return DefaultSingleRequestContextDecoratingFieldMaterializationDataFetcher<Any?>(
            reactiveDataFetcher
        )
    }
}
