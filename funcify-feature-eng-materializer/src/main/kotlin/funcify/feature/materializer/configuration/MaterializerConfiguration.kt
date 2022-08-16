package funcify.feature.materializer.configuration

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.metadata.alias.GraphQLApiDataSourceAliasProvider
import funcify.feature.datasource.graphql.metadata.temporal.GraphQLApiDataSourceLastUpdatedAttributeProvider
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.json.JsonMapper
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
import funcify.feature.materializer.service.DefaultMaterializationGraphQLWiringFactory
import funcify.feature.materializer.service.DefaultMaterializationPreparsedDocumentProvider
import funcify.feature.materializer.service.DefaultSingleRequestFieldMaterializationGraphService
import funcify.feature.materializer.service.MaterializationGraphQLWiringFactory
import funcify.feature.materializer.service.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.service.SingleRequestFieldMaterializationGraphService
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.GraphQLSchema
import java.util.concurrent.Executor
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
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
        restApiDataSources: ObjectProvider<RestApiDataSource>,
        schematicVertexGraphRemappingStrategyProvider:
            ObjectProvider<SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>>
    ): MetamodelGraph {
        return sequenceOf(graphQLApiDataSources, restApiDataSources)
            .flatMap { dsProvider -> dsProvider }
            .fold(metamodelGraphFactory.builder()) { builder, ds ->
                when (ds) {
                    is GraphQLApiDataSource -> {
                        // first, add datasource
                        // then, any alias providers
                        // last, any last_updated temporal attribute providers
                        graphQLApiDataSourceLastUpdatedAttributeProviders.fold(
                            graphQLApiDataSourceAliasProviders.fold(builder.addDataSource(ds)) {
                                bldr,
                                prov ->
                                bldr.addAttributeAliasProviderForDataSource(prov, ds)
                            }
                        ) { bldr, prov ->
                            bldr.addLastUpdatedAttributeProviderForDataSource(prov, ds)
                        }
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
            .peek(
                { mmg: MetamodelGraph ->
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
                },
                { t: Throwable ->
                    logger.error(
                        """metamodel_graph: [ status: failed ] 
                           |[ message: ${t.message} ]
                           |""".flatten(),
                        t
                    )
                }
            )
            .blockForFirst()
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
        asyncExecutor: Executor,
        singleRequestFieldMaterializationGraphService: SingleRequestFieldMaterializationGraphService
    ): SingleRequestFieldMaterializationDataFetcherFactory {
        return DefaultSingleRequestFieldMaterializationDataFetcherFactory(
            asyncExecutor = asyncExecutor,
            singleRequestFieldMaterializationGraphService =
                singleRequestFieldMaterializationGraphService
        )
    }

    @ConditionalOnMissingBean(value = [SingleRequestFieldMaterializationGraphService::class])
    @Bean
    fun singleRequestFieldMaterializationGraphService(
        schematicPathBasedJsonRetrievalFunctionFactory:
            SchematicPathBasedJsonRetrievalFunctionFactory
    ): SingleRequestFieldMaterializationGraphService {
        return DefaultSingleRequestFieldMaterializationGraphService(
            schematicPathBasedJsonRetrievalFunctionFactory
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationPreparsedDocumentProvider::class])
    @Bean
    fun materializationPreparsedDocumentProvider(
        jsonMapper: JsonMapper
    ): MaterializationPreparsedDocumentProvider {
        return DefaultMaterializationPreparsedDocumentProvider(jsonMapper)
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
            DefaultMaterializationMetamodel(metamodelGraph, materializationGraphQLSchema)
        )
        return broker
    }
}
