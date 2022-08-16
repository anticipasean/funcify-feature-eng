package funcify.feature.datasource.configuration

import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategyProvider
import funcify.feature.datasource.retrieval.DefaultSchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.datasource.sdl.impl.DefaultSchematicVertexSDLDefinitionCreationContextFactory
import kotlinx.collections.immutable.toPersistentSet
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 *
 * @author smccarron
 * @created 2022-07-02
 */
@Configuration
class DataSourceConfiguration {

    @ConditionalOnMissingBean(value = [SchematicVertexSDLDefinitionCreationContextFactory::class])
    @Bean
    fun schematicVertexSDLDefinitionCreationContextFactory():
        SchematicVertexSDLDefinitionCreationContextFactory {
        return DefaultSchematicVertexSDLDefinitionCreationContextFactory()
    }

    @ConditionalOnMissingBean(value = [SchematicPathBasedJsonRetrievalFunctionFactory::class])
    @Bean
    fun schematicPathBasedJsonRetrievalFunctionFactory(
        strategyProviders: ObjectProvider<DataSourceSpecificJsonRetrievalStrategyProvider<*>>
    ): SchematicPathBasedJsonRetrievalFunctionFactory {
        return DefaultSchematicPathBasedJsonRetrievalFunctionFactory(
            strategyProviders.toPersistentSet()
        )
    }
}
