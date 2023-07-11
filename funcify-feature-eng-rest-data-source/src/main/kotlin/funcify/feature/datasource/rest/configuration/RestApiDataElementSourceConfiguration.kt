package funcify.feature.datasource.rest.configuration

import funcify.feature.datasource.rest.RestApiServiceFactory
import funcify.feature.datasource.rest.factory.DefaultRestApiServiceFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RestApiDataElementSourceConfiguration {

    @ConditionalOnMissingBean(value = [RestApiServiceFactory::class])
    @Bean
    fun restApiServiceFactory(
        webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
        codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
    ): RestApiServiceFactory {
        return DefaultRestApiServiceFactory(
            webClientCustomizerProvider = webClientCustomizerProvider,
            codecCustomizerProvider = codecCustomizerProvider
        )
    }
}
