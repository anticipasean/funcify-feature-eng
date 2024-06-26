package funcify.feature.materializer.configuration

import funcify.feature.materializer.coordinator.DefaultGraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.coordinator.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.dispatch.DefaultSingleRequestMaterializationDispatchService
import funcify.feature.materializer.dispatch.SingleRequestMaterializationDispatchService
import funcify.feature.materializer.document.DefaultMaterializationPreparsedDocumentProvider
import funcify.feature.materializer.document.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.executor.DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy
import funcify.feature.materializer.executor.DefaultSingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.materializer.executor.GraphQLSingleRequestExecutor
import funcify.feature.materializer.executor.GraphQLSingleRequestMaterializationQueryExecutionStrategy
import funcify.feature.materializer.executor.SingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.materializer.executor.SingleRequestMaterializationExecutionStrategyInstrumentation
import funcify.feature.materializer.executor.SpringGraphQLSingleRequestExecutor
import funcify.feature.materializer.fetcher.DefaultSingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.materializer.fetcher.DefaultSingleRequestFieldValueMaterializer
import funcify.feature.materializer.fetcher.SingleRequestFieldValueMaterializer
import funcify.feature.materializer.graph.DefaultSingleRequestMaterializationGraphService
import funcify.feature.materializer.graph.SingleRequestMaterializationGraphService
import funcify.feature.materializer.input.DefaultSingleRequestRawInputContextExtractor
import funcify.feature.materializer.input.SingleRequestRawInputContextExtractor
import funcify.feature.materializer.model.DefaultMaterializationMetamodelBuildStrategy
import funcify.feature.materializer.model.DefaultMaterializationMetamodelFactory
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.materializer.model.MaterializationMetamodelBuildStrategy
import funcify.feature.materializer.model.MaterializationMetamodelFactory
import funcify.feature.materializer.output.DefaultSingleRequestJsonFieldValueDeserializer
import funcify.feature.materializer.output.SingleRequestJsonFieldValueDeserializer
import funcify.feature.materializer.request.factory.DefaultRawGraphQLRequestFactory
import funcify.feature.materializer.request.factory.RawGraphQLRequestFactory
import funcify.feature.materializer.response.DefaultSingleRequestMaterializationTabularResponsePostprocessingService
import funcify.feature.materializer.response.SingleRequestMaterializationTabularResponsePostprocessingService
import funcify.feature.materializer.response.factory.DefaultSerializedGraphQLResponseFactory
import funcify.feature.materializer.response.factory.SerializedGraphQLResponseFactory
import funcify.feature.materializer.schema.DefaultMaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.DefaultMaterializationMetamodelBroker
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.materializer.session.factory.DefaultGraphQLSingleRequestSessionFactory
import funcify.feature.materializer.session.factory.GraphQLSingleRequestSessionFactory
import funcify.feature.materializer.type.MaterializationInterfaceSubtypeResolverFactory
import funcify.feature.materializer.type.SubtypingDirectiveInterfaceSubtypeResolverFactory
import funcify.feature.materializer.wiring.DefaultMaterializationGraphQLWiringFactory
import funcify.feature.materializer.wiring.MaterializationGraphQLWiringFactory
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.tracking.TrackableValueFactory
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import graphql.execution.DataFetcherResult
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.DataFetcherFactory
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.CompletionStage

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

    @ConditionalOnMissingBean(value = [SingleRequestJsonFieldValueDeserializer::class])
    @Bean
    fun singleRequestJsonFieldValueDeserializer(): SingleRequestJsonFieldValueDeserializer {
        return DefaultSingleRequestJsonFieldValueDeserializer()
    }

    @ConditionalOnMissingBean(value = [SingleRequestFieldValueMaterializer::class])
    @Bean
    fun singleRequestFieldValueMaterializer(
        jsonMapper: JsonMapper,
        singleRequestJsonFieldValueDeserializer: SingleRequestJsonFieldValueDeserializer
    ): SingleRequestFieldValueMaterializer {
        return DefaultSingleRequestFieldValueMaterializer(
            jsonMapper = jsonMapper,
            singleRequestJsonFieldValueDeserializer = singleRequestJsonFieldValueDeserializer
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestMaterializationGraphService::class])
    @Bean
    fun singleRequestMaterializationGraphService(): SingleRequestMaterializationGraphService {
        return DefaultSingleRequestMaterializationGraphService()
    }

    @ConditionalOnMissingBean(value = [SingleRequestMaterializationDispatchService::class])
    @Bean
    fun singleRequestMaterializationDispatchService(
        jsonMapper: JsonMapper,
        trackableValueFactory: TrackableValueFactory
    ): SingleRequestMaterializationDispatchService {
        return DefaultSingleRequestMaterializationDispatchService(
            jsonMapper = jsonMapper,
            trackableValueFactory = trackableValueFactory
        )
    }

    @ConditionalOnMissingBean(value = [DataFetcherFactory::class])
    @Bean
    fun dataFetcherFactory(
        singleRequestMaterializationOrchestratorService: SingleRequestFieldValueMaterializer,
    ): DataFetcherFactory<CompletionStage<out DataFetcherResult<Any?>>> {
        return DefaultSingleRequestFieldMaterializationDataFetcherFactory(
            singleRequestFieldValueMaterializer = singleRequestMaterializationOrchestratorService
        )
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
        featureEngineeringModel: FeatureEngineeringModel,
        materializationGraphQLSchemaFactory: MaterializationGraphQLSchemaFactory
    ): GraphQLSchema {
        return materializationGraphQLSchemaFactory
            .createGraphQLSchemaFromMetamodel(featureEngineeringModel)
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

    @ConditionalOnMissingBean(value = [MaterializationMetamodelBuildStrategy::class])
    @Bean
    fun materializationMetamodelBuildStrategy(): MaterializationMetamodelBuildStrategy {
        return DefaultMaterializationMetamodelBuildStrategy()
    }

    @ConditionalOnMissingBean(value = [MaterializationMetamodelFactory::class])
    @Bean
    fun materializationMetamodelFactory(
        materializationMetamodelBuildStrategy: MaterializationMetamodelBuildStrategy
    ): MaterializationMetamodelFactory {
        return DefaultMaterializationMetamodelFactory(
            materializationMetamodelBuildStrategy = materializationMetamodelBuildStrategy
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationMetamodel::class])
    @Bean
    fun materializationMetamodel(
        featureEngineeringModel: FeatureEngineeringModel,
        materializationGraphQLSchema: GraphQLSchema,
        materializationMetamodelFactory: MaterializationMetamodelFactory
    ): MaterializationMetamodel {
        return materializationMetamodelFactory
            .builder()
            .featureEngineeringModel(featureEngineeringModel)
            .materializationGraphQLSchema(materializationGraphQLSchema)
            .build()
            .doOnNext { mm: MaterializationMetamodel ->
                logger.debug("build_materialization_metamodel: [ status: successful ]")
            }
            .doOnError { t: Throwable ->
                logger.error(
                    "build_materialization_metamodel: [ status: failed ][ type: {}, message: {} ]",
                    t::class.simpleName,
                    t.message
                )
            }
            .toFuture()
            .join()
    }

    @ConditionalOnMissingBean(value = [MaterializationMetamodelBroker::class])
    @Bean
    fun materializationMetamodelBroker(
        materializationMetamodel: MaterializationMetamodel
    ): MaterializationMetamodelBroker {
        val broker: MaterializationMetamodelBroker = DefaultMaterializationMetamodelBroker()
        broker.pushNewMaterializationMetamodel(materializationMetamodel)
        return broker
    }

    @ConditionalOnMissingBean(value = [SingleRequestRawInputContextExtractor::class])
    @Bean
    fun singleRequestRawInputContextExtractor(
        jsonMapper: JsonMapper
    ): SingleRequestRawInputContextExtractor {
        return DefaultSingleRequestRawInputContextExtractor(jsonMapper = jsonMapper)
    }

    @ConditionalOnMissingBean(value = [GraphQLSingleRequestSessionFactory::class])
    @Bean
    fun graphQLSingleRequestSessionFactory(
        materializationMetamodelBroker: MaterializationMetamodelBroker,
        singleRequestRawInputContextExtractor: SingleRequestRawInputContextExtractor
    ): GraphQLSingleRequestSessionFactory {
        return DefaultGraphQLSingleRequestSessionFactory(
            materializationMetamodelBroker = materializationMetamodelBroker,
            singleRequestRawInputContextExtractor = singleRequestRawInputContextExtractor,
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationPreparsedDocumentProvider::class])
    @Bean
    fun materializationPreparsedDocumentProvider(
        jsonMapper: JsonMapper,
        singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
        singleRequestMaterializationDispatchService: SingleRequestMaterializationDispatchService
    ): MaterializationPreparsedDocumentProvider {
        return DefaultMaterializationPreparsedDocumentProvider(
            jsonMapper = jsonMapper,
            singleRequestMaterializationGraphService = singleRequestMaterializationGraphService,
            singleRequestMaterializationDispatchService =
                singleRequestMaterializationDispatchService
        )
    }

    @ConditionalOnMissingBean(
        value = [SingleRequestMaterializationTabularResponsePostprocessingService::class]
    )
    @Bean
    fun singleRequestMaterializationTabularResponsePostprocessingService(
        jsonMapper: JsonMapper,
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory
    ): SingleRequestMaterializationTabularResponsePostprocessingService {
        return DefaultSingleRequestMaterializationTabularResponsePostprocessingService(
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
        singleRequestMaterializationTabularResponsePostprocessingService:
            SingleRequestMaterializationTabularResponsePostprocessingService
    ): SingleRequestMaterializationExecutionResultPostprocessingService {
        return DefaultSingleRequestMaterializationExecutionResultPostprocessingService(
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory,
            singleRequestMaterializationTabularResponsePostprocessingService =
                singleRequestMaterializationTabularResponsePostprocessingService
        )
    }

    @ConditionalOnMissingBean(
        value = [GraphQLSingleRequestMaterializationQueryExecutionStrategy::class]
    )
    @Bean
    fun queryAsyncExecutionStrategy(): GraphQLSingleRequestMaterializationQueryExecutionStrategy {
        return DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy()
    }

    @Bean
    fun singleRequestMaterializationExecutionStrategyInstrumentation(
        jsonMapper: JsonMapper
    ): Instrumentation {
        return SingleRequestMaterializationExecutionStrategyInstrumentation(jsonMapper)
    }

    @ConditionalOnMissingBean(value = [GraphQLSingleRequestSessionCoordinator::class])
    @Bean
    fun graphQLSingleRequestSessionCoordinator(
        materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
        instrumentation: Instrumentation,
        queryAsyncExecutionStrategy: GraphQLSingleRequestMaterializationQueryExecutionStrategy,
        singleRequestMaterializationExecutionResultPostprocessingService:
            SingleRequestMaterializationExecutionResultPostprocessingService,
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
    ): GraphQLSingleRequestSessionCoordinator {
        return DefaultGraphQLSingleRequestSessionCoordinator(
            materializationPreparsedDocumentProvider = materializationPreparsedDocumentProvider,
            queryAsyncExecutionStrategy = queryAsyncExecutionStrategy,
            instrumentation = instrumentation,
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
