package funcify.feature.materializer.configuration

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.metadata.alias.GraphQLApiDataSourceAliasProvider
import funcify.feature.datasource.graphql.metadata.identifier.GraphQLApiDataSourceEntityIdentifiersProvider
import funcify.feature.datasource.graphql.metadata.temporal.GraphQLApiDataSourceLastUpdatedAttributeProvider
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.tracking.TrackableJsonValuePublisherProvider
import funcify.feature.datasource.tracking.TrackableValueFactory
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.context.DefaultMaterializationGraphContextFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.DefaultSingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationDataFetcherFactory
import funcify.feature.materializer.request.DefaultRawGraphQLRequestFactory
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.response.DefaultSerializedGraphQLResponseFactory
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.schema.DefaultMaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.DefaultMaterializationMetamodel
import funcify.feature.materializer.schema.DefaultMaterializationMetamodelBroker
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaFactory
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import funcify.feature.materializer.schema.edge.DefaultRequestParameterEdgeFactory
import funcify.feature.materializer.service.*
import funcify.feature.materializer.session.DefaultGraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.DefaultGraphQLSingleRequestSessionFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.toTry
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.execution.ExecutionStrategy
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaPrinter
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MaterializerConfiguration {

    companion object {
        private val logger: Logger = loggerFor<MaterializerConfiguration>()
    }

    @Bean
    fun metamodelGraph(
        metamodelGraphFactory: MetamodelGraphFactory,
        graphQLApiDataSources: ObjectProvider<GraphQLApiDataSource>,
        graphQLApiDataSourceAliasProviders: ObjectProvider<GraphQLApiDataSourceAliasProvider>,
        graphQLApiDataSourceLastUpdatedAttributeProviders:
            ObjectProvider<GraphQLApiDataSourceLastUpdatedAttributeProvider>,
        graphQLApiDataSourceEntityIdentifiersProviders:
            ObjectProvider<GraphQLApiDataSourceEntityIdentifiersProvider>,
        restApiDataSources: ObjectProvider<RestApiDataSource>,
        schematicVertexGraphRemappingStrategyProvider:
            ObjectProvider<SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>>
    ): MetamodelGraph {
        return sequenceOf(graphQLApiDataSources, restApiDataSources)
            .flatMap { dsProvider -> dsProvider }
            .fold(metamodelGraphFactory.builder()) { builder, ds ->
                when (ds) {
                    is GraphQLApiDataSource -> {
                        graphQLApiDataSourceEntityIdentifiersProviders.fold(
                            graphQLApiDataSourceLastUpdatedAttributeProviders.fold(
                                graphQLApiDataSourceAliasProviders.fold(
                                    builder.addDataSource(ds)
                                ) { bldr, prov ->
                                    bldr.addAttributeAliasProviderForDataSource(prov, ds)
                                }
                            ) { bldr, prov ->
                                bldr.addLastUpdatedAttributeProviderForDataSource(prov, ds)
                            }
                        ) { bldr, prov -> bldr.addEntityIdentifiersProviderForDataSource(prov, ds) }
                    }
                    // break out any other datasource specific providers into separate cases or
                    // create generic function to add the other providers when support for them
                    // starts
                    else -> {
                        builder.addDataSource(ds)
                    }
                }
            }
            .let { builder: MetamodelGraph.Builder ->
                schematicVertexGraphRemappingStrategyProvider.fold(builder) { bldr, strategy ->
                    builder.addRemappingStrategyForPostProcessingSchematicVertices(strategy)
                }
            }
            .build()
            .doOnNext { mmg: MetamodelGraph ->
                val firstVertexPath: String =
                    mmg.toOption()
                        .filter { m -> m.pathBasedGraph.vertices.size > 0 }
                        .map { m -> m.pathBasedGraph.vertices[0].path.toString() }
                        .getOrElse { "<NA>" }
                logger.info(
                    """metamodel_graph: [ status: success ] 
                            |[ metamodel_graph [ vertices.size: ${mmg.pathBasedGraph.vertices.size}, 
                            |vertices[0].path: $firstVertexPath ] ]
                            |""".flatten()
                )
            }
            .doOnError { t: Throwable ->
                logger.error(
                    """metamodel_graph: [ status: failed ] 
                    |[ message: ${t.message} ]
                    |""".flatten(),
                    t
                )
            }
            .toTry()
            .orElseThrow { t: Throwable ->
                when (t) {
                    is FeatureEngCommonException -> t
                    else -> {
                        MaterializerException(
                            MaterializerErrorResponse.METAMODEL_GRAPH_CREATION_ERROR,
                            t
                        )
                    }
                }
            }
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

    @ConditionalOnMissingBean(value = [MaterializationGraphQLSchemaFactory::class])
    @Bean
    fun materializationGraphQLSchemaFactory(
        jsonMapper: JsonMapper,
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        sdlDefinitionCreationContextFactory: SchematicVertexSDLDefinitionCreationContextFactory,
        sdlDefinitionImplementationStrategyProvider:
            ObjectProvider<SchematicVertexSDLDefinitionImplementationStrategy>,
        materializationGraphQLWiringFactory: MaterializationGraphQLWiringFactory
    ): MaterializationGraphQLSchemaFactory {
        return DefaultMaterializationGraphQLSchemaFactory(
            jsonMapper = jsonMapper,
            scalarTypeRegistry =
                scalarTypeRegistryProvider.getIfAvailable {
                    ScalarTypeRegistry.materializationRegistry()
                },
            sdlDefinitionCreationContextFactory = sdlDefinitionCreationContextFactory,
            sdlDefinitionImplementationStrategies =
                sdlDefinitionImplementationStrategyProvider.toList(),
            materializationGraphQLWiringFactory = materializationGraphQLWiringFactory
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationGraphQLWiringFactory::class])
    @Bean
    fun materializationGraphQLWiringFactory(
        metamodelGraph: MetamodelGraph,
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        singleRequestFieldMaterializationDataFetcherFactory:
            SingleRequestFieldMaterializationDataFetcherFactory
    ): MaterializationGraphQLWiringFactory {
        return DefaultMaterializationGraphQLWiringFactory(
            scalarTypeRegistry =
                scalarTypeRegistryProvider.getIfAvailable {
                    ScalarTypeRegistry.materializationRegistry()
                },
            singleRequestFieldMaterializationDataFetcherFactory =
                singleRequestFieldMaterializationDataFetcherFactory
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestFieldMaterializationDataFetcherFactory::class])
    @Bean
    fun singleRequestFieldMaterializationDataFetcherFactory(
        singleRequestMaterializationOrchestratorService:
            SingleRequestMaterializationOrchestratorService
    ): SingleRequestFieldMaterializationDataFetcherFactory {
        return DefaultSingleRequestFieldMaterializationDataFetcherFactory(
            singleRequestMaterializationOrchestratorService =
                singleRequestMaterializationOrchestratorService
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestMaterializationGraphService::class])
    @Bean
    fun singleRequestMaterializationGraphService(
        jsonMapper: JsonMapper
    ): SingleRequestMaterializationGraphService {
        return DefaultSingleRequestMaterializationGraphService(
            materializationGraphContextFactory = DefaultMaterializationGraphContextFactory(),
            materializationGraphConnector =
                DefaultMaterializationGraphConnector(
                    jsonMapper = jsonMapper,
                    requestParameterEdgeFactory = DefaultRequestParameterEdgeFactory()
                )
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestMaterializationDispatchService::class])
    @Bean
    fun singleRequestMaterializationDispatchService(
        schematicPathBasedJsonRetrievalFunctionFactory:
            SchematicPathBasedJsonRetrievalFunctionFactory,
        trackableValueFactory: TrackableValueFactory,
        materializedTrackableValuePublishingService: MaterializedTrackableValuePublishingService
    ): SingleRequestMaterializationDispatchService {
        return DefaultSingleRequestMaterializationDispatchService(
            schematicPathBasedJsonRetrievalFunctionFactory =
                schematicPathBasedJsonRetrievalFunctionFactory,
            trackableValueFactory = trackableValueFactory,
            materializedTrackableValuePublishingService =
                materializedTrackableValuePublishingService
        )
    }

    @ConditionalOnMissingBean(value = [MaterializedTrackableValuePublishingService::class])
    @Bean
    fun materializedTrackableValuePublishingService(
        jsonMapper: JsonMapper,
        trackableJsonValuePublisherProvider: ObjectProvider<TrackableJsonValuePublisherProvider>
    ): MaterializedTrackableValuePublishingService {
        return DefaultMaterializedTrackableValuePublishingService(
            jsonMapper = jsonMapper,
            trackableJsonValuePublisherProvider =
                trackableJsonValuePublisherProvider.getIfAvailable {
                    TrackableJsonValuePublisherProvider.NO_OP_PROVIDER
                }
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestMaterializationOrchestratorService::class])
    @Bean
    fun singleRequestMaterializationOrchestratorService(
        jsonMapper: JsonMapper,
        materializedTrackableValuePublishingService: MaterializedTrackableValuePublishingService
    ): SingleRequestMaterializationOrchestratorService {
        return DefaultSingleRequestMaterializationOrchestratorService(jsonMapper = jsonMapper)
    }

    @ConditionalOnMissingBean(value = [MaterializationPreparsedDocumentProvider::class])
    @Bean
    fun materializationPreparsedDocumentProvider(
        jsonMapper: JsonMapper
    ): MaterializationPreparsedDocumentProvider {
        return DefaultMaterializationPreparsedDocumentProvider(jsonMapper = jsonMapper)
    }

    @ConditionalOnMissingBean(value = [GraphQLSchema::class])
    @Bean
    fun materializationGraphQLSchema(
        metamodelGraph: MetamodelGraph,
        materializationGraphQLSchemaFactory: MaterializationGraphQLSchemaFactory
    ): GraphQLSchema {
        return materializationGraphQLSchemaFactory
            .createGraphQLSchemaFromMetamodelGraph(metamodelGraph)
            .peek(
                { gs: GraphQLSchema ->
                    logger.info(
                        """materialization_graphql_schema: [ status: success ] 
                            |[ graphql_schema.query_type.field_definitions.size: 
                            |${gs.queryType.fieldDefinitions.size} ]
                            |""".flatten()
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
        metamodelGraph: MetamodelGraph,
        materializationGraphQLSchema: GraphQLSchema
    ): MaterializationMetamodelBroker {
        val broker: MaterializationMetamodelBroker = DefaultMaterializationMetamodelBroker()
        broker.pushNewMaterializationMetamodel(
            DefaultMaterializationMetamodel(
                metamodelGraph = metamodelGraph,
                materializationGraphQLSchema = materializationGraphQLSchema
            )
        )
        return broker
    }

    @Bean
    fun graphQLSingleRequestSessionFactory(
        materializationMetamodelBroker: MaterializationMetamodelBroker
    ): GraphQLSingleRequestSessionFactory {
        return DefaultGraphQLSingleRequestSessionFactory(
            materializationMetamodelBroker = materializationMetamodelBroker
        )
    }

    @Bean
    fun graphQLSingleRequestSessionCoordinator(
        serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
        materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
        materializationQueryExecutionStrategy:
            GraphQLSingleRequestMaterializationQueryExecutionStrategy
    ): GraphQLSingleRequestSessionCoordinator {
        return DefaultGraphQLSingleRequestSessionCoordinator(
            serializedGraphQLResponseFactory = serializedGraphQLResponseFactory,
            materializationPreparsedDocumentProvider = materializationPreparsedDocumentProvider,
            materializationQueryExecutionStrategy = materializationQueryExecutionStrategy
        )
    }

    @ConditionalOnMissingBean(value = [ExecutionStrategy::class])
    @Bean
    fun graphQLSingleRequestMaterializationQueryExecutionStrategy(
        @Value("\${feature-eng-service.graphql.execution-strategy-timeout-millis:-1}")
        globalExecutionStrategyTimeoutSeconds: Long,
        singleRequestMaterializationGraphService: SingleRequestMaterializationGraphService,
        singleRequestMaterializationDispatchService: SingleRequestMaterializationDispatchService
    ): GraphQLSingleRequestMaterializationQueryExecutionStrategy {
        return DefaultGraphQLSingleRequestMaterializationQueryExecutionStrategy(
            globalExecutionStrategyTimeoutMilliseconds = globalExecutionStrategyTimeoutSeconds,
            singleRequestMaterializationGraphService = singleRequestMaterializationGraphService,
            singleRequestMaterializationPreprocessingService =
                singleRequestMaterializationDispatchService
        )
    }
}
