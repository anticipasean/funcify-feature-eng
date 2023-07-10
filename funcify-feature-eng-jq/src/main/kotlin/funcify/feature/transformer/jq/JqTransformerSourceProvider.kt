package funcify.feature.transformer.jq

import funcify.feature.schema.SourceType
import funcify.feature.schema.transformer.TransformerSourceProvider
import funcify.feature.tools.container.attempt.Try
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JqTransformerSourceProvider : TransformerSourceProvider<JqTransformerSource> {

    override val name: String

    override val sourceType: SourceType
        get() = JqSourceType

    override fun getLatestTransformerSource(): Mono<out JqTransformerSource>

    interface Builder {

        fun name(name: String): Builder

        fun transformerYamlFile(classpathResource: ClassPathResource): Builder

        fun build(): Try<JqTransformerSourceProvider>
    }
}
