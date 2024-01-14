package funcify.feature.file.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.file.FileRegistryFeatureCalculatorProvider
import funcify.feature.file.FileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.metadata.FileRegistryMetadataProvider
import funcify.feature.file.source.DefaultFileRegistryFeatureCalculator
import funcify.feature.schema.sdl.SDLDefinitionsSetExtractor
import funcify.feature.schema.sdl.TypeDefinitionRegistryFilter
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.success
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.ResultExtensions.toMono
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-16
 */
internal class DefaultFileRegistryFeatureCalculatorProviderFactory(
    private val classpathResourceRegistryMetadataProvider:
        FileRegistryMetadataProvider<ClassPathResource>,
    private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter
) : FileRegistryFeatureCalculatorProviderFactory {

    companion object {

        internal class DefaultBuilder(
            private val classpathResourceRegistryMetadataProvider:
                FileRegistryMetadataProvider<ClassPathResource>,
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter,
            private var name: String? = null,
            private var schemaClasspathResource: ClassPathResource? = null
        ) : FileRegistryFeatureCalculatorProvider.Builder {

            override fun name(name: String): FileRegistryFeatureCalculatorProvider.Builder =
                this.apply { this.name = name }

            override fun graphQLSchemaClasspathResource(
                schemaClasspathResource: ClassPathResource
            ): FileRegistryFeatureCalculatorProvider.Builder =
                this.apply { this.schemaClasspathResource = schemaClasspathResource }

            override fun build(): Try<FileRegistryFeatureCalculatorProvider> {
                return eagerEffect<String, FileRegistryFeatureCalculatorProvider> {
                        ensureNotNull(name) { "name is null" }
                        ensureNotNull(schemaClasspathResource) { "schemaClasspathResource is null" }
                        DefaultFileRegistryFeatureCalculatorProvider(
                            classpathResourceRegistryMetadataProvider =
                                classpathResourceRegistryMetadataProvider,
                            typeDefinitionRegistryFilter = typeDefinitionRegistryFilter,
                            name = name!!,
                            graphQLSchemaClasspathResource = schemaClasspathResource!!
                        )
                    }
                    .fold(
                        { message: String ->
                            Try.failure(
                                ServiceError.of(
                                    """unable to create 
                                    |graphql_schema_file_feature_calculator_provider: 
                                    |[ message: %s ]"""
                                        .flatten(),
                                    message
                                )
                            )
                        },
                        ::success
                    )
            }
        }

        internal data class DefaultFileRegistryFeatureCalculatorProvider(
            private val classpathResourceRegistryMetadataProvider:
                FileRegistryMetadataProvider<ClassPathResource>,
            private val typeDefinitionRegistryFilter: TypeDefinitionRegistryFilter,
            override val name: String,
            private val graphQLSchemaClasspathResource: ClassPathResource
        ) : FileRegistryFeatureCalculatorProvider {

            companion object {
                private val logger: Logger =
                    loggerFor<DefaultFileRegistryFeatureCalculatorProvider>()
            }

            override fun getLatestSource(): Mono<out FileRegistryFeatureCalculator> {
                logger.info("get_latest_source: [ name: {} ]", name)
                return classpathResourceRegistryMetadataProvider
                    .provideTypeDefinitionRegistry(graphQLSchemaClasspathResource)
                    .flatMap { tdr: TypeDefinitionRegistry ->
                        typeDefinitionRegistryFilter.filter(tdr).toMono()
                    }
                    .map { tdr: TypeDefinitionRegistry ->
                        DefaultFileRegistryFeatureCalculator(
                            name = name,
                            sourceSDLDefinitions = SDLDefinitionsSetExtractor.invoke(tdr)
                        )
                    }
            }
        }

    }

    override fun builder(): FileRegistryFeatureCalculatorProvider.Builder {
        return DefaultBuilder(
            classpathResourceRegistryMetadataProvider = classpathResourceRegistryMetadataProvider,
            typeDefinitionRegistryFilter = typeDefinitionRegistryFilter,
        )
    }

}
