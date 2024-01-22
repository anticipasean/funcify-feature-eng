package funcify.feature.file

import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.sdl.transformer.TypeDefinitionRegistryTransformer
import funcify.feature.tools.container.attempt.Try
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-16
 */
interface FileRegistryFeatureCalculatorProvider :
    FeatureCalculatorProvider<FileRegistryFeatureCalculator> {

    override val name: String

    override fun getLatestSource(): Mono<out FileRegistryFeatureCalculator>

    interface Builder {

        fun name(name: String): Builder

        fun graphQLSchemaClasspathResource(schemaClasspathResource: ClassPathResource): Builder

        fun addTypeDefinitionRegistryTransformer(
            typeDefinitionRegistryTransformer: TypeDefinitionRegistryTransformer
        ): Builder

        fun build(): Try<FileRegistryFeatureCalculatorProvider>
    }
}
