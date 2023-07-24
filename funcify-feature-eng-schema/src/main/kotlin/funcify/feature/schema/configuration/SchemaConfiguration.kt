package funcify.feature.schema.configuration

import funcify.feature.directive.MaterializationDirectiveRegistry
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.MetamodelFactory
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.factory.DefaultMetamodelFactory
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.strategy.DefaultMetamodelBuildStrategy
import funcify.feature.schema.transformer.TransformerSourceProvider
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration //
class SchemaConfiguration {

    companion object {
        private val logger: Logger = loggerFor<SchemaConfiguration>()
    }

    @ConditionalOnMissingBean(value = [ScalarTypeRegistry::class])
    @Bean
    fun scalarTypeRegistry(): ScalarTypeRegistry {
        return ScalarTypeRegistry.materializationRegistry()
    }

    @ConditionalOnMissingBean(value = [MaterializationDirectiveRegistry::class])
    @Bean
    fun materializationDirectiveRegistry(): MaterializationDirectiveRegistry {
        return MaterializationDirectiveRegistry.standardRegistry()
    }

    @ConditionalOnMissingBean(value = [MetamodelBuildStrategy::class])
    @Bean
    fun metamodelBuildStrategy(
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        materializationDirectiveRegistryProvider: ObjectProvider<MaterializationDirectiveRegistry>
    ): MetamodelBuildStrategy {
        return DefaultMetamodelBuildStrategy(
            scalarTypeRegistry =
                scalarTypeRegistryProvider.getIfAvailable {
                    ScalarTypeRegistry.materializationRegistry()
                },
            materializationDirectiveRegistry =
                materializationDirectiveRegistryProvider.getIfAvailable {
                    MaterializationDirectiveRegistry.standardRegistry()
                }
        )
    }

    @ConditionalOnMissingBean(value = [MetamodelFactory::class])
    @Bean
    fun metamodelFactory(metamodelBuildStrategy: MetamodelBuildStrategy): MetamodelFactory {
        return DefaultMetamodelFactory(metamodelBuildStrategy = metamodelBuildStrategy)
    }

    @ConditionalOnMissingBean(value = [Metamodel::class])
    @Bean
    fun metamodel(
        metamodelFactory: MetamodelFactory,
        transformerSourceProviders: ObjectProvider<TransformerSourceProvider<*>>,
        dataElementSourceProviders: ObjectProvider<DataElementSourceProvider<*>>,
        featureCalculatorProviders: ObjectProvider<FeatureCalculatorProvider<*>>,
        featureJsonValueStoreProviders: ObjectProvider<FeatureJsonValueStore>,
        featureJsonValuePublisherProviders: ObjectProvider<FeatureJsonValuePublisher>
    ): Metamodel {
        val builder: Metamodel.Builder = metamodelFactory.builder()
        transformerSourceProviders
            .asSequence()
            .fold(builder, Metamodel.Builder::addTransformerSourceProvider)
        dataElementSourceProviders
            .asSequence()
            .fold(builder, Metamodel.Builder::addDataElementSourceProvider)
        featureCalculatorProviders
            .asSequence()
            .fold(builder, Metamodel.Builder::addFeatureCalculatorProvider)
        featureJsonValueStoreProviders
            .asSequence()
            .fold(builder, Metamodel.Builder::addFeatureJsonValueStore)
        featureJsonValuePublisherProviders
            .asSequence()
            .fold(builder, Metamodel.Builder::addFeatureJsonValuePublisher)
        return builder
            .build()
            .doOnError { t: Throwable ->
                logger.error(
                    "build_metamodel: [ status failed ][ type: {}, message: {} ]",
                    t::class.simpleName,
                    t.message
                )
            }
            .doOnNext { mm: Metamodel ->
                logger.info(
                    "build_metamodel: [ status: success ][ metamodel.type_definition_registry.query_object_type.field_definitions.name: {} ]",
                    mm.typeDefinitionRegistry
                        .getType("Query", ObjectTypeDefinition::class.java)
                        .map(ObjectTypeDefinition::getFieldDefinitions)
                        .map(List<FieldDefinition>::asSequence)
                        .orElseGet(::emptySequence)
                        .map(FieldDefinition::getName)
                        .joinToString(", ", "{ ", " }")
                )
            }
            .toFuture()
            .join()
    }
}
