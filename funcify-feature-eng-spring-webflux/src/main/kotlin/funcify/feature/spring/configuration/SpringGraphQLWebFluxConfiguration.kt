package funcify.feature.spring.configuration

import funcify.feature.json.JsonMapper
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.materializer.service.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.spring.router.GraphQLWebFluxHandlerFunction
import funcify.feature.spring.service.GraphQLSingleRequestExecutor
import funcify.feature.spring.service.SpringGraphQLSingleRequestExecutor
import funcify.feature.spring.session.SpringGraphQLSingleRequestSessionCoordinator
import funcify.feature.spring.session.SpringGraphQLSingleRequestSessionFactory
import graphql.execution.ExecutionStrategy
import java.util.concurrent.Executor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Configuration
class SpringGraphQLWebFluxConfiguration {

    companion object {
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
    }

    @Bean
    fun springGraphQLSingleRequestSessionFactory(
        materializationMetamodelBroker: MaterializationMetamodelBroker
    ): GraphQLSingleRequestSessionFactory {
        return SpringGraphQLSingleRequestSessionFactory(
            materializationMetamodelBroker = materializationMetamodelBroker
        )
    }

    @Bean
    fun springGraphQLSingleRequestSessionCoordinator(
        asyncExecutor: Executor,
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
        materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
        materializationExecutionStrategy: ExecutionStrategy
    ): GraphQLSingleRequestSessionCoordinator {
        return SpringGraphQLSingleRequestSessionCoordinator(
            asyncExecutor = asyncExecutor,
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory,
            materializationPreparsedDocumentProvider = materializationPreparsedDocumentProvider,
            materializationExecutionStrategy = materializationExecutionStrategy
        )
    }

    @Bean
    fun springGraphQLSingleRequestExecutor(
        graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
        graphQLSingleRequestSessionCoordinator: GraphQLSingleRequestSessionCoordinator
    ): GraphQLSingleRequestExecutor {
        return SpringGraphQLSingleRequestExecutor(
            graphQLSingleRequestSessionFactory = graphQLSingleRequestSessionFactory,
            graphQLSingleRequestSessionCoordinator = graphQLSingleRequestSessionCoordinator
        )
    }

    @Bean
    fun graphQLWebFluxRouterFunction(
        @Value("\${feature-eng-service.graphql.path}") graphQLPath: String,
        jsonMapper: JsonMapper,
        graphQLSingleRequestExecutor: GraphQLSingleRequestExecutor,
        rawGraphQLRequestFactory: RawGraphQLRequestFactory,
        graphQLExecutionInputCustomizerProvider: ObjectProvider<GraphQLExecutionInputCustomizer>
    ): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                graphQLPath,
                RequestPredicates.accept(
                    MediaType.APPLICATION_JSON,
                    MediaType.valueOf(MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE)
                ),
                GraphQLWebFluxHandlerFunction(
                    jsonMapper = jsonMapper,
                    graphQLSingleRequestExecutor = graphQLSingleRequestExecutor,
                    rawGraphQLRequestFactory = rawGraphQLRequestFactory,
                    graphQLExecutionInputCustomizers =
                        graphQLExecutionInputCustomizerProvider.toList()
                )
            )
            .build()
    }
}
