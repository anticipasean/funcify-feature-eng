package funcify.feature.file.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.file.FileRegistryFeatureCalculatorProvider
import funcify.feature.file.FileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.metadata.provider.FileRegistryMetadataProvider
import funcify.feature.file.source.DefaultFileRegistryFeatureCalculator
import funcify.feature.schema.sdl.SDLDefinitionsSetExtractor
import funcify.feature.schema.sdl.transformer.CompositeTypeDefinitionRegistryTransformer
import funcify.feature.schema.sdl.transformer.TypeDefinitionRegistryTransformer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.success
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
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
    private val typeDefinitionRegistryTransformers: List<TypeDefinitionRegistryTransformer> =
        listOf()
) : FileRegistryFeatureCalculatorProviderFactory {

    companion object {

        internal class DefaultBuilder(
            private val classpathResourceRegistryMetadataProvider:
                FileRegistryMetadataProvider<ClassPathResource>,
            private val typeDefinitionRegistryTransformers:
                PersistentList.Builder<TypeDefinitionRegistryTransformer> =
                persistentListOf<TypeDefinitionRegistryTransformer>().builder(),
            private var name: String? = null,
            private var schemaClasspathResource: ClassPathResource? = null
        ) : FileRegistryFeatureCalculatorProvider.Builder {

            override fun name(name: String): FileRegistryFeatureCalculatorProvider.Builder =
                this.apply { this.name = name }

            override fun graphQLSchemaClasspathResource(
                schemaClasspathResource: ClassPathResource
            ): FileRegistryFeatureCalculatorProvider.Builder =
                this.apply { this.schemaClasspathResource = schemaClasspathResource }

            override fun addTypeDefinitionRegistryTransformer(
                typeDefinitionRegistryTransformer: TypeDefinitionRegistryTransformer
            ): FileRegistryFeatureCalculatorProvider.Builder =
                this.apply {
                    this.typeDefinitionRegistryTransformers.add(typeDefinitionRegistryTransformer)
                }

            override fun build(): Try<FileRegistryFeatureCalculatorProvider> {
                return eagerEffect<String, FileRegistryFeatureCalculatorProvider> {
                        ensureNotNull(name) { "name is null" }
                        ensureNotNull(schemaClasspathResource) { "schemaClasspathResource is null" }
                        DefaultFileRegistryFeatureCalculatorProvider(
                            classpathResourceRegistryMetadataProvider =
                                classpathResourceRegistryMetadataProvider,
                            typeDefinitionRegistryTransformer =
                                CompositeTypeDefinitionRegistryTransformer(
                                    typeDefinitionRegistryTransformers.build()
                                ),
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
            private val typeDefinitionRegistryTransformer: TypeDefinitionRegistryTransformer,
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
                        typeDefinitionRegistryTransformer.transform(tdr)
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
            typeDefinitionRegistryTransformers =
                typeDefinitionRegistryTransformers.toPersistentList().builder()
        )
    }
}
