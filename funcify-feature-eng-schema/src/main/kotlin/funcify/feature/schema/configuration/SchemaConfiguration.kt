package funcify.feature.schema.configuration

import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.factory.DefaultMetamodelGraphFactory
import funcify.feature.schema.factory.DefaultSchematicVertexFactory
import funcify.feature.schema.factory.DefaultSourcePathTransformer
import funcify.feature.schema.factory.MetamodelGraphFactory
import funcify.feature.schema.factory.SchematicVertexFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SchemaConfiguration {

    @ConditionalOnMissingBean(value = [SourcePathTransformer::class])
    @Bean
    fun sourcePathTransformer(): SourcePathTransformer {
        return DefaultSourcePathTransformer()
    }

    @ConditionalOnMissingBean(value = [SchematicVertexFactory::class])
    @Bean
    fun schematicVertexFactory(): SchematicVertexFactory {
        return DefaultSchematicVertexFactory()
    }

    @ConditionalOnMissingBean(value = [MetamodelGraphFactory::class])
    @Bean
    fun metamodelGraphFactory(
        schematicVertexFactory: SchematicVertexFactory,
        sourcePathTransformer: SourcePathTransformer
    ): MetamodelGraphFactory {
        return DefaultMetamodelGraphFactory(
            schematicVertexFactory = schematicVertexFactory,
            sourcePathTransformer = sourcePathTransformer
        )
    }
}
