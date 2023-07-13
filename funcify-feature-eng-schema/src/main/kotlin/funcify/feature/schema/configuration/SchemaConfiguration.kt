package funcify.feature.schema.configuration

import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.MetamodelFactory
import funcify.feature.schema.factory.DefaultMetamodelFactory
import funcify.feature.schema.strategy.DefaultMetamodelBuildStrategy
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration //
class SchemaConfiguration {

    @ConditionalOnMissingBean(value = [ScalarTypeRegistry::class])
    @Bean
    fun scalarTypeRegistry(): ScalarTypeRegistry {
        return ScalarTypeRegistry.materializationRegistry()
    }

    @ConditionalOnMissingBean(value = [MetamodelBuildStrategy::class])
    @Bean
    fun metamodelBuildStrategy(scalarTypeRegistry: ScalarTypeRegistry): MetamodelBuildStrategy {
        return DefaultMetamodelBuildStrategy(scalarTypeRegistry = scalarTypeRegistry)
    }

    @ConditionalOnMissingBean(value = [MetamodelFactory::class])
    @Bean
    fun metamodelFactory(metamodelBuildStrategy: MetamodelBuildStrategy): MetamodelFactory {
        return DefaultMetamodelFactory(metamodelBuildStrategy = metamodelBuildStrategy)
    }
}
