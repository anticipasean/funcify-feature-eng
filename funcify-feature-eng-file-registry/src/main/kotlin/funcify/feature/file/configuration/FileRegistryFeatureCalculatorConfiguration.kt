package funcify.feature.file.configuration

import funcify.feature.file.FileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.factory.DefaultFileRegistryFeatureCalculatorProviderFactory
import funcify.feature.file.metadata.provider.FeatureGraphQLSchemaClasspathResourceMetadataProvider
import funcify.feature.file.metadata.provider.FileRegistryMetadataProvider
import funcify.feature.file.metadata.transformer.TransformAnnotatedFeatureDefinitionsTransformer
import funcify.feature.schema.sdl.transformer.CompositeTypeDefinitionRegistryTransformer
import funcify.feature.schema.sdl.transformer.TypeDefinitionRegistryTransformer
import org.springframework.beans.factory.ObjectProvider
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
    fun classpathResourceFileRegistryMetadataProvider(): FileRegistryMetadataProvider<ClassPathResource> {
        return FeatureGraphQLSchemaClasspathResourceMetadataProvider()
    }

    @ConditionalOnMissingBean(value = [FileRegistryFeatureCalculatorProviderFactory::class])
    @Bean
    fun fileRegistryFeatureCalculatorProviderFactory(
        classpathResourceFileRegistryMetadataProvider: FileRegistryMetadataProvider<ClassPathResource>,
        typeDefinitionRegistryTransformerProvider: ObjectProvider<TypeDefinitionRegistryTransformer>
    ): FileRegistryFeatureCalculatorProviderFactory {
        return DefaultFileRegistryFeatureCalculatorProviderFactory(
            classpathResourceRegistryMetadataProvider =
                classpathResourceFileRegistryMetadataProvider,
            typeDefinitionRegistryTransformer =
                CompositeTypeDefinitionRegistryTransformer(
                    typeDefinitionRegistryTransformers =
                        typeDefinitionRegistryTransformerProvider
                            .asSequence()
                            .plus(TransformAnnotatedFeatureDefinitionsTransformer())
                            .toList()
                )
        )
    }
}
