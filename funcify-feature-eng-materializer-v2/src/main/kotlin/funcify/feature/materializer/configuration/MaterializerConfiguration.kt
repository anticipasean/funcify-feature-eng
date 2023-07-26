package funcify.feature.materializer.configuration

import funcify.feature.materializer.context.document.DefaultColumnarDocumentContextFactory
import funcify.feature.materializer.document.DefaultMaterializationPreparsedDocumentProvider
import funcify.feature.materializer.document.DefaultSingleRequestMaterializationColumnarDocumentPreprocessingService
import funcify.feature.materializer.document.DefaultSingleRequestMaterializationColumnarResponsePostprocessingService
import funcify.feature.materializer.document.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.document.SingleRequestMaterializationColumnarDocumentPreprocessingService
import funcify.feature.materializer.document.SingleRequestMaterializationColumnarResponsePostprocessingService
import funcify.feature.materializer.fetcher.DefaultSingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.materializer.fetcher.DummyReactiveDataFetcher
import funcify.feature.materializer.fetcher.ReactiveDataFetcher
import funcify.feature.materializer.request.DefaultRawGraphQLRequestFactory
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.response.DefaultSerializedGraphQLResponseFactory
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.schema.DefaultMaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.DefaultMaterializationMetamodel
import funcify.feature.materializer.schema.DefaultMaterializationMetamodelBroker
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.materializer.service.DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy
import funcify.feature.materializer.service.DefaultSingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.materializer.service.GraphQLSingleRequestExecutor
import funcify.feature.materializer.service.SingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.materializer.service.SpringGraphQLSingleRequestExecutor
import funcify.feature.materializer.session.DefaultGraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.DefaultGraphQLSingleRequestSessionFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.materializer.type.MaterializationInterfaceSubtypeResolverFactory
import funcify.feature.materializer.type.SubtypingDirectiveInterfaceSubtypeResolverFactory
import funcify.feature.materializer.wiring.DefaultMaterializationGraphQLWiringFactory
import funcify.feature.materializer.wiring.MaterializationGraphQLWiringFactory
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.Metamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import java.util.concurrent.CompletionStage
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author smccarron
 * @created 2023-07-21
 */
@Configuration
class MaterializerConfiguration {

    companion object {
        private val logger: Logger = loggerFor<MaterializerConfiguration>()
    }

    @ConditionalOnMissingBean(value = [RawGraphQLRequestFactory::class])
    @Bean
    fun rawGraphQLRequestFactory(): RawGraphQLRequestFactory {
        return DefaultRawGraphQLRequestFactory()
    }

    @ConditionalOnMissingBean(value = [SerializedGraphQLResponseFactory::class])
    @Bean
    fun serializedGraphQLResponseFactory(): SerializedGraphQLResponseFactory {
        return DefaultSerializedGraphQLResponseFactory()
    }

    @ConditionalOnMissingBean(value = [ReactiveDataFetcher::class])
    @Bean
    fun reactiveDataFetcher(): ReactiveDataFetcher<Any> {
        return DummyReactiveDataFetcher()
    }

    @ConditionalOnMissingBean(value = [DataFetcherFactory::class])
    @Bean
    fun dataFetcherFactory(
        reactiveDataFetcher: ReactiveDataFetcher<Any>
    ): DataFetcherFactory<CompletionStage<out DataFetcherResult<Any?>>> {
        return DefaultSingleRequestFieldMaterializationDataFetcherFactory(reactiveDataFetcher)
    }

    @ConditionalOnMissingBean(value = [MaterializationInterfaceSubtypeResolverFactory::class])
    @Bean
    fun materializationInterfaceSubtypeResolverFactory():
        MaterializationInterfaceSubtypeResolverFactory {
        return SubtypingDirectiveInterfaceSubtypeResolverFactory()
    }

    @ConditionalOnMissingBean(value = [MaterializationGraphQLWiringFactory::class])
    @Bean
    fun materializationGraphQLWiringFactory(
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        dataFetcherFactory: DataFetcherFactory<CompletionStage<out DataFetcherResult<Any?>>>,
        materializationInterfaceSubtypeResolverFactory:
            MaterializationInterfaceSubtypeResolverFactory,
    ): MaterializationGraphQLWiringFactory {
        return DefaultMaterializationGraphQLWiringFactory(
            scalarTypeRegistry =
                scalarTypeRegistryProvider.getIfAvailable {
                    ScalarTypeRegistry.materializationRegistry()
                },
            dataFetcherFactory = dataFetcherFactory,
            materializationInterfaceSubtypeResolverFactory =
                materializationInterfaceSubtypeResolverFactory,
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationGraphQLSchemaFactory::class])
    @Bean
    fun materializationGraphQLSchemaFactory(
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        materializationGraphQLWiringFactory: MaterializationGraphQLWiringFactory
    ): MaterializationGraphQLSchemaFactory {
        return DefaultMaterializationGraphQLSchemaFactory(
            materializationGraphQLWiringFactory = materializationGraphQLWiringFactory
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLSchema::class])
    @Bean
    fun materializationGraphQLSchema(
        metamodel: Metamodel,
        materializationGraphQLSchemaFactory: MaterializationGraphQLSchemaFactory
    ): GraphQLSchema {
        return materializationGraphQLSchemaFactory
            .createGraphQLSchemaFromMetamodel(metamodel)
            .peek(
                { gs: GraphQLSchema ->
                    logger.info(
                        """materialization_graphql_schema: [ status: success ] 
                            |[ graphql_schema.query_type.field_definitions.size: 
                            |${gs.queryType.fieldDefinitions.size} ]
                            |"""
                            .flatten()
                    )
                    logger.info("materialization_graphql_schema: \n{}", SchemaPrinter().print(gs))
                },
                { t: Throwable ->
                    logger.error("materialization_graphql_schema: [ status: failed ]", t)
                }
            )
            .orElseThrow()
    }

    @ConditionalOnMissingBean(value = [MaterializationMetamodelBroker::class])
    @Bean
    fun materializationMetamodelBroker(
        metamodel: Metamodel,
        materializationGraphQLSchema: GraphQLSchema
    ): MaterializationMetamodelBroker {
        val broker: MaterializationMetamodelBroker = DefaultMaterializationMetamodelBroker()
        broker.pushNewMaterializationMetamodel(
            DefaultMaterializationMetamodel(
                metamodel = metamodel,
                materializationGraphQLSchema = materializationGraphQLSchema
            )
        )
        return broker
    }

    @ConditionalOnMissingBean(value = [GraphQLSingleRequestSessionFactory::class])
    @Bean
    fun graphQLSingleRequestSessionFactory(
        materializationMetamodelBroker: MaterializationMetamodelBroker
    ): GraphQLSingleRequestSessionFactory {
        return DefaultGraphQLSingleRequestSessionFactory(
            materializationMetamodelBroker = materializationMetamodelBroker
        )
    }

    @ConditionalOnMissingBean(
        value = [SingleRequestMaterializationColumnarDocumentPreprocessingService::class]
    )
    @Bean
    fun singleRequestMaterializationColumnarDocumentPreprocessingService(
        jsonMapper: JsonMapper
    ): SingleRequestMaterializationColumnarDocumentPreprocessingService {
        return DefaultSingleRequestMaterializationColumnarDocumentPreprocessingService(
            jsonMapper = jsonMapper,
            columnarDocumentContextFactory = DefaultColumnarDocumentContextFactory()
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationPreparsedDocumentProvider::class])
    @Bean
    fun materializationPreparsedDocumentProvider(
        jsonMapper: JsonMapper,
        singleRequestMaterializationColumnarDocumentPreprocessingService:
            SingleRequestMaterializationColumnarDocumentPreprocessingService
    ): MaterializationPreparsedDocumentProvider {
        return DefaultMaterializationPreparsedDocumentProvider(
            jsonMapper = jsonMapper,
            singleRequestMaterializationColumnarDocumentPreprocessingService =
                singleRequestMaterializationColumnarDocumentPreprocessingService
        )
    }

    @ConditionalOnMissingBean(
        value = [SingleRequestMaterializationColumnarResponsePostprocessingService::class]
    )
    @Bean
    fun singleRequestMaterializationColumnarResponsePostprocessingService(
        jsonMapper: JsonMapper,
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory
    ): SingleRequestMaterializationColumnarResponsePostprocessingService {
        return DefaultSingleRequestMaterializationColumnarResponsePostprocessingService(
            jsonMapper = jsonMapper,
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory
        )
    }

    @ConditionalOnMissingBean(
        value = [SingleRequestMaterializationExecutionResultPostprocessingService::class]
    )
    @Bean
    fun singleRequestMaterializationExecutionResultPostprocessingService(
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
        singleRequestMaterializationColumnarResponsePostprocessingService:
            SingleRequestMaterializationColumnarResponsePostprocessingService
    ): SingleRequestMaterializationExecutionResultPostprocessingService {
        return DefaultSingleRequestMaterializationExecutionResultPostprocessingService(
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory,
            singleRequestMaterializationColumnarResponsePostprocessingService =
                singleRequestMaterializationColumnarResponsePostprocessingService
        )
    }

    @ConditionalOnMissingBean(value = [AsyncExecutionStrategy::class])
    @Bean
    fun queryAsyncExecutionStrategy(): AsyncExecutionStrategy {
        return DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy()
    }

    @ConditionalOnMissingBean(value = [GraphQLSingleRequestSessionCoordinator::class])
    @Bean
    fun graphQLSingleRequestSessionCoordinator(
        materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
        queryAsyncExecutionStrategy: AsyncExecutionStrategy,
        singleRequestMaterializationExecutionResultPostprocessingService:
            SingleRequestMaterializationExecutionResultPostprocessingService,
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
    ): GraphQLSingleRequestSessionCoordinator {
        return DefaultGraphQLSingleRequestSessionCoordinator(
            materializationPreparsedDocumentProvider = materializationPreparsedDocumentProvider,
            queryAsyncExecutionStrategy = queryAsyncExecutionStrategy,
            singleRequestMaterializationExecutionResultPostprocessingService =
                singleRequestMaterializationExecutionResultPostprocessingService,
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLSingleRequestExecutor::class])
    @Bean
    fun springGraphQLSingleRequestExecutor(
        graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
        graphQLSingleRequestSessionCoordinator: GraphQLSingleRequestSessionCoordinator,
    ): GraphQLSingleRequestExecutor {
        return SpringGraphQLSingleRequestExecutor(
            graphQLSingleRequestSessionFactory = graphQLSingleRequestSessionFactory,
            graphQLSingleRequestSessionCoordinator = graphQLSingleRequestSessionCoordinator
        )
    }
}
