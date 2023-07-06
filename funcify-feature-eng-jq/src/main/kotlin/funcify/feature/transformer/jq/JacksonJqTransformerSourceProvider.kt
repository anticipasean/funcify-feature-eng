package funcify.feature.transformer.jq

import funcify.feature.schema.SourceType
import funcify.feature.schema.transformer.TransformerSourceProvider
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-02
 */
interface JacksonJqTransformerSourceProvider :
    TransformerSourceProvider<JacksonJqTransformerSource> {

    override val name: String

    override val sourceType: SourceType
        get() = JacksonJqSourceType

    override fun getLatestTransformerSource(): Mono<JacksonJqTransformerSource>

    interface Builder {

        fun name(name: String): Builder

        fun transformerYamlFile(classpathResource: ClassPathResource): Builder

        fun build(): JacksonJqTransformerSourceProvider

    }
}
