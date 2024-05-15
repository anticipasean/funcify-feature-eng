package funcify.feature.transformer.jq.configuration

import funcify.feature.tools.json.JsonMapper
import funcify.feature.transformer.jq.JqTransformerFactory
import funcify.feature.transformer.jq.JqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.factory.DefaultJqTransformerFactory
import funcify.feature.transformer.jq.factory.DefaultJqTransformerSourceProviderFactory
import funcify.feature.transformer.jq.metadata.DefaultJqTransformerTypeDefinitionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author smccarron
 * @created 2023-07-10
 */
@Configuration
class JqTransformerConfiguration {

    @ConditionalOnMissingBean(value = [JqTransformerFactory::class])
    @Bean
    fun jqTransformerFactory(): JqTransformerFactory {
        return DefaultJqTransformerFactory()
    }

    @ConditionalOnMissingBean(value = [JqTransformerTypeDefinitionFactory::class])
    @Bean
    fun jqTransformerTypeDefinitionFactory(): JqTransformerTypeDefinitionFactory {
        return DefaultJqTransformerTypeDefinitionFactory
    }

    @ConditionalOnMissingBean(value = [JqTransformerSourceProviderFactory::class])
    @Bean
    fun jqTransformerSourceProviderFactory(
        jsonMapper: JsonMapper,
        jqTransformerFactory: JqTransformerFactory,
        jqTransformerTypeDefinitionFactory: JqTransformerTypeDefinitionFactory
    ): JqTransformerSourceProviderFactory {
        return DefaultJqTransformerSourceProviderFactory(
            jsonMapper = jsonMapper,
            jqTransformerFactory = jqTransformerFactory,
            jqTransformerTypeDefinitionFactory = jqTransformerTypeDefinitionFactory
        )
    }
}
