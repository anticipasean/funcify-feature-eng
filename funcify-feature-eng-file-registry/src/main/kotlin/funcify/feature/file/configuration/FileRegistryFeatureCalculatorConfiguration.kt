package funcify.feature.file.configuration

import funcify.feature.file.FileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.factory.DefaultFileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.metadata.FeatureGraphQLSchemaClasspathResourceMetadataProvider
import funcify.feature.file.metadata.FileRegistryMetadataProvider
import funcify.feature.file.metadata.filter.TransformAnnotatedFeatureDefinitionsFilter
import funcify.feature.schema.sdl.CompositeTypeDefinitionRegistryFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

/**
 * @author smccarron
 * @created 2023-08-17
 */
@Configuration
class FileRegistryFeatureCalculatorConfiguration {

    @Bean
    fun classpathResourceFileRegistryMetadataProvider():
        FileRegistryMetadataProvider<ClassPathResource> {
        return FeatureGraphQLSchemaClasspathResourceMetadataProvider()
    }

    @ConditionalOnMissingBean(value = [FileRegistryFeatureCalculatorProviderFactory::class])
    @Bean
    fun fileRegistryFeatureCalculatorProviderFactory(
        classpathResourceFileRegistryMetadataProvider:
            FileRegistryMetadataProvider<ClassPathResource>
    ): FileRegistryFeatureCalculatorProviderFactory {
        return DefaultFileRegistryFeatureCalculatorProviderFactory(
            classpathResourceRegistryMetadataProvider =
                classpathResourceFileRegistryMetadataProvider,
            typeDefinitionRegistryFilter =
                CompositeTypeDefinitionRegistryFilter(
                    typeDefinitionRegistryFilters =
                        listOf(TransformAnnotatedFeatureDefinitionsFilter())
                )
        )
    }
}
