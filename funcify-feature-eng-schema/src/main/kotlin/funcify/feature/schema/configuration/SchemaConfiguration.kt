package funcify.feature.schema.configuration

import funcify.feature.schema.factory.DefaultMetamodelGraphFactory
import funcify.feature.schema.factory.DefaultSchematicVertexFactory
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.schema.factory.SchematicVertexFactory
import funcify.feature.schema.strategy.CompositeSchematicVertexGraphRemappingStrategy
import funcify.feature.schema.strategy.SchematicVertexGraphSourceIndexBasedRemappingStrategy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SchemaConfiguration {

    @ConditionalOnMissingBean(value = [SchematicVertexFactory::class])
    @Bean
    fun schematicVertexFactory(): SchematicVertexFactory {
        return DefaultSchematicVertexFactory()
    }

    @ConditionalOnMissingBean(value = [MetamodelGraphFactory::class])
    @Bean
    fun metamodelGraphFactory(
        schematicVertexFactory: SchematicVertexFactory,
        schematicVertexGraphRemappingStrategyProvider:
            ObjectProvider<SchematicVertexGraphSourceIndexBasedRemappingStrategy>
    ): MetamodelGraphFactory {
        return DefaultMetamodelGraphFactory(
            schematicVertexFactory = schematicVertexFactory,
            schematicVertexGraphRemappingStrategy =
                CompositeSchematicVertexGraphRemappingStrategy(
                    schematicVertexGraphRemappingStrategyProvider.toList()
                )
        )
    }
}
