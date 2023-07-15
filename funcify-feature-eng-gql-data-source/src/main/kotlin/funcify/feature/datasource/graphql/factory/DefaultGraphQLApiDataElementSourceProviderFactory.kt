package funcify.feature.datasource.graphql.factory

import arrow.core.continuations.eagerEffect
import funcify.feature.datasource.graphql.GraphQLApiDataElementSource
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProvider
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProviderFactory
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.SchemaOnlyDataElementSource
import funcify.feature.datasource.graphql.ServiceBackedDataElementSource
import funcify.feature.datasource.graphql.metadata.filter.CompositeTypeDefinitionRegistryFilter
import funcify.feature.datasource.graphql.metadata.filter.TypeDefinitionRegistryFilter
import funcify.feature.datasource.graphql.metadata.provider.GraphQLApiSchemaFileMetadataProvider
import funcify.feature.datasource.graphql.metadata.provider.GraphQLApiServiceMetadataProvider
import funcify.feature.error.ServiceError
import funcify.feature.schema.sdl.SDLDefinitionsSetExtractor
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.ResultExtensions.toMono
import funcify.feature.tools.json.JsonMapper
import graphql.language.SDLDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentSet
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

internal class DefaultGraphQLApiDataElementSourceProviderFactory(
    private val jsonMapper: JsonMapper,
    private val typeDefinitionRegistryFilters: List<TypeDefinitionRegistryFilter> = listOf()
) : GraphQLApiDataElementSourceProviderFactory {

    companion object {

        internal class DefaultBuilder(
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter,
            private val jsonMapper: JsonMapper,
            private var name: String? = null,
            private var service: GraphQLApiService? = null,
            private var schemaClassPathResource: ClassPathResource? = null
        ) : GraphQLApiDataElementSourceProvider.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultBuilder>()
            }

            override fun name(name: String): GraphQLApiDataElementSourceProvider.Builder {
                this.name = name
                return this
            }

            override fun graphQLApiService(
                service: GraphQLApiService
            ): GraphQLApiDataElementSourceProvider.Builder {
                this.service = service
                return this
            }

            override fun graphQLSchemaClasspathResource(
                schemaClassPathResource: ClassPathResource
            ): GraphQLApiDataElementSourceProvider.Builder {
                this.schemaClassPathResource = schemaClassPathResource
                return this
            }

            override fun build(): Try<GraphQLApiDataElementSourceProvider> {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "build: [ graphql_api_data_element_source_provider.name: {} ]",
                        name
                    )
                }
                return eagerEffect<String, GraphQLApiDataElementSourceProvider> {
                        ensure(name != null) { "name of data_element_source not provided" }
                        ensure(service != null || schemaClassPathResource != null) {
                            "neither a service instance nor a classpath resource mapping to a graphql schema has been provided"
                        }
                        when {
                            schemaClassPathResource != null && service != null -> {
                                DefaultServiceAndSchemaBackedDataElementSourceProvider(
                                    name = name!!,
                                    graphQLApiService = service!!,
                                    schemaClassPathResource = schemaClassPathResource!!,
                                    typeDefinitionRegistryFilter = typeDefinitionRegistryFilter,
                                )
                            }
                            schemaClassPathResource != null -> {
                                DefaultSchemaBackedDataElementSourceProvider(
                                    name = name!!,
                                    schemaClassPathResource = schemaClassPathResource!!,
                                    typeDefinitionRegistryFilter = typeDefinitionRegistryFilter,
                                )
                            }
                            else -> {
                                DefaultServiceBackedDataElementSourceProvider(
                                    name = name!!,
                                    graphQLApiService = service!!,
                                    metadataProvider =
                                        GraphQLApiServiceMetadataProvider(jsonMapper),
                                    typeDefinitionRegistryFilter = typeDefinitionRegistryFilter,
                                )
                            }
                        }
                    }
                    .fold(
                        { message: String ->
                            logger.error(
                                "build: [ status: failed ][ graphql_api_data_element_source_provider.name: {} ][ message: {} ]",
                                name,
                                message
                            )
                            Try.failure(ServiceError.of(message))
                        },
                        { p: GraphQLApiDataElementSourceProvider -> Try.success(p) }
                    )
            }
        }

        internal class DefaultServiceAndSchemaBackedDataElementSourceProvider(
            override val name: String,
            private val graphQLApiService: GraphQLApiService,
            private val schemaClassPathResource: ClassPathResource,
            private val metadataProvider: GraphQLApiSchemaFileMetadataProvider =
                GraphQLApiSchemaFileMetadataProvider(),
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter
        ) : GraphQLApiDataElementSourceProvider {

            companion object {
                private val logger: Logger =
                    loggerFor<DefaultServiceAndSchemaBackedDataElementSourceProvider>()
            }

            override fun getLatestSource(): Mono<GraphQLApiDataElementSource> {
                logger.info("get_latest_data_element_source: [ name: {} ]", name)
                return metadataProvider
                    .provideTypeDefinitionRegistry(schemaClassPathResource)
                    .flatMap { tdr: TypeDefinitionRegistry ->
                        typeDefinitionRegistryFilter.filter(tdr).toMono()
                    }
                    .map { tdr: TypeDefinitionRegistry ->
                        DefaultServiceBackedDataElementSource(
                            name = name,
                            sourceSDLDefinitions = SDLDefinitionsSetExtractor(tdr),
                            graphQLApiService = graphQLApiService
                        )
                    }
            }
        }

        internal class DefaultServiceBackedDataElementSourceProvider(
            override val name: String,
            private val graphQLApiService: GraphQLApiService,
            private val metadataProvider: GraphQLApiServiceMetadataProvider,
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter
        ) : GraphQLApiDataElementSourceProvider {

            companion object {
                private val logger: Logger =
                    loggerFor<DefaultServiceBackedDataElementSourceProvider>()
            }

            override fun getLatestSource(): Mono<GraphQLApiDataElementSource> {
                logger.info("get_latest_data_element_source: [ name: {} ]", name)
                return metadataProvider
                    .provideTypeDefinitionRegistry(graphQLApiService)
                    .flatMap { td: TypeDefinitionRegistry ->
                        typeDefinitionRegistryFilter.filter(td).toMono()
                    }
                    .map { tdr: TypeDefinitionRegistry ->
                        DefaultServiceBackedDataElementSource(
                            name = name,
                            sourceSDLDefinitions = SDLDefinitionsSetExtractor(tdr),
                            graphQLApiService = graphQLApiService
                        )
                    }
            }
        }

        internal class DefaultSchemaBackedDataElementSourceProvider(
            override val name: String,
            private val schemaClassPathResource: ClassPathResource,
            private val metadataProvider: GraphQLApiSchemaFileMetadataProvider =
                GraphQLApiSchemaFileMetadataProvider(),
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter
        ) : GraphQLApiDataElementSourceProvider {

            companion object {
                private val logger: Logger =
                    loggerFor<DefaultSchemaBackedDataElementSourceProvider>()
            }

            override fun getLatestSource(): Mono<GraphQLApiDataElementSource> {
                logger.info("get_latest_data_element_source: [ name: {} ]", name)
                return metadataProvider
                    .provideTypeDefinitionRegistry(schemaClassPathResource)
                    .flatMap { tdr: TypeDefinitionRegistry ->
                        typeDefinitionRegistryFilter.filter(tdr).toMono()
                    }
                    .map { tdr: TypeDefinitionRegistry ->
                        DefaultSchemaOnlyDataElementSource(
                            name = name,
                            sourceSDLDefinitions = SDLDefinitionsSetExtractor(tdr)
                        )
                    }
            }
        }

        internal class DefaultServiceBackedDataElementSource(
            override val name: String,
            override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>,
            override val graphQLApiService: GraphQLApiService
        ) : ServiceBackedDataElementSource {}

        internal class DefaultSchemaOnlyDataElementSource(
            override val name: String,
            override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
        ) : SchemaOnlyDataElementSource {}
    }

    override fun builder(): GraphQLApiDataElementSourceProvider.Builder {
        return DefaultBuilder(
            jsonMapper = jsonMapper,
            typeDefinitionRegistryFilter =
                CompositeTypeDefinitionRegistryFilter(typeDefinitionRegistryFilters)
        )
    }
}
