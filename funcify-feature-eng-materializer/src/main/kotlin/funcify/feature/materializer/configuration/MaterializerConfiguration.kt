package funcify.feature.materializer.configuration

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.sdl.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.request.DefaultRawGraphQLRequestFactory
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.sdl.DefaultGraphQLObjectTypeDefinitionFactory
import funcify.feature.materializer.sdl.DefaultGraphQLSDLFieldDefinitionFactory
import funcify.feature.materializer.schema.DefaultMaterializationGraphQLSchemaBroker
import funcify.feature.materializer.schema.DefaultMaterializationGraphQLSchemaFactory
import funcify.feature.materializer.sdl.GraphQLObjectTypeDefinitionFactory
import funcify.feature.materializer.sdl.GraphQLSDLFieldDefinitionFactory
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaBroker
import funcify.feature.materializer.schema.MaterializationGraphQLSchemaFactory
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.toImmutableList
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class MaterializerConfiguration {

    companion object {
        private val logger: Logger = loggerFor<MaterializerConfiguration>()
    }

    @Bean
    fun metamodelGraph(
        metamodelGraphFactory: MetamodelGraphFactory,
        graphQLApiDataSources: ObjectProvider<GraphQLApiDataSource>,
        restApiDataSources: ObjectProvider<RestApiDataSource>
    ): MetamodelGraph {
        return sequenceOf(graphQLApiDataSources, restApiDataSources)
            .flatMap { dsProvider -> dsProvider }
            .fold(metamodelGraphFactory.builder()) { bldr, ds -> bldr.addDataSource(ds) }
            .build()
            .peek(
                { mmg: MetamodelGraph ->
                    val firstVertexPath: String =
                        mmg.toOption()
                            .filter { m -> m.vertices.size > 0 }
                            .map { m -> m.vertices[0].path.toString() }
                            .getOrElse { "<NA>" }
                    logger.info(
                        """metamodel_graph: [ status: success ] 
                            |[ metamodel_graph [ vertices.size: ${mmg.vertices.size}, 
                            |vertices[0].path: $firstVertexPath ] ]
                            |""".flattenIntoOneLine()
                    )
                },
                { t: Throwable ->
                    logger.error(
                        """metamodel_graph: [ status: failed ] 
                            |[ type: ${t::class.simpleName}, 
                            |message: ${t.message} ]
                            |""".flattenIntoOneLine()
                    )
                }
            )
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

    @ConditionalOnMissingBean(value = [GraphQLObjectTypeDefinitionFactory::class])
    @Bean
    fun graphQLObjectTypeDefinitionFactory(
        sourceIndexGqlSdlDefinitionFactories: ObjectProvider<SourceIndexGqlSdlDefinitionFactory<*>>
    ): GraphQLObjectTypeDefinitionFactory {
        return DefaultGraphQLObjectTypeDefinitionFactory(
            sourceIndexGqlSdlDefinitionFactories =
                sourceIndexGqlSdlDefinitionFactories.toImmutableList()
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLSDLFieldDefinitionFactory::class])
    @Bean
    fun graphQLSDLFieldDefinitionFactory(
        sourceIndexGqlSdlDefinitionFactories: ObjectProvider<SourceIndexGqlSdlDefinitionFactory<*>>
    ): GraphQLSDLFieldDefinitionFactory {
        return DefaultGraphQLSDLFieldDefinitionFactory(
            sourceIndexGqlSdlDefinitionFactories =
                sourceIndexGqlSdlDefinitionFactories.toImmutableList()
        )
    }

    @ConditionalOnMissingBean(value = [MaterializationGraphQLSchemaFactory::class])
    @Bean
    fun materializationGraphQLSchemaFactory(
        objectMapper: ObjectMapper,
        graphQLObjectTypeDefinitionFactory: GraphQLObjectTypeDefinitionFactory,
        graphQLSDLFieldDefinitionFactory: GraphQLSDLFieldDefinitionFactory
    ): MaterializationGraphQLSchemaFactory {
        return DefaultMaterializationGraphQLSchemaFactory(
            objectMapper = objectMapper,
            graphQLObjectTypeDefinitionFactory = graphQLObjectTypeDefinitionFactory,
            graphQLSDLFieldDefinitionFactory = graphQLSDLFieldDefinitionFactory
        )
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
                            |""".flattenIntoOneLine()
                    )
                },
                { t: Throwable ->
                    logger.error("materialization_graphql_schema: [ status: failed ]", t)
                }
            )
            .orElseThrow()
    }

    @ConditionalOnMissingBean(value = [MaterializationGraphQLSchemaBroker::class])
    @Bean
    fun materializationGraphQLSchemaBroker(
        materializationGraphQLSchema: GraphQLSchema
    ): MaterializationGraphQLSchemaBroker {
        val broker: MaterializationGraphQLSchemaBroker = DefaultMaterializationGraphQLSchemaBroker()
        broker.pushNewMaterializationSchema(materializationSchema = materializationGraphQLSchema)
        return broker
    }
}
