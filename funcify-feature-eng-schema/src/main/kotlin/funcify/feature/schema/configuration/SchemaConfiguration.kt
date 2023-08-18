package funcify.feature.schema.configuration

import funcify.feature.directive.MaterializationDirectiveRegistry
import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.FeatureEngineeringModelBuildStrategy
import funcify.feature.schema.FeatureEngineeringModelFactory
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.factory.DefaultFeatureEngineeringModelFactory
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.sdl.UnsupportedDirectivesTypeDefinitionRegistryFilter
import funcify.feature.schema.strategy.DefaultFeatureEngineeringModelBuildStrategy
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

    @Bean
    fun unsupportedDirectivesTypeDefinitionRegistryFilter(
        materializationDirectiveRegistryProvider: ObjectProvider<MaterializationDirectiveRegistry>,
    ): UnsupportedDirectivesTypeDefinitionRegistryFilter {
        return UnsupportedDirectivesTypeDefinitionRegistryFilter(
            materializationDirectiveRegistry =
                materializationDirectiveRegistryProvider.getIfAvailable {
                    MaterializationDirectiveRegistry.standardRegistry()
                }
        )
    }

    @ConditionalOnMissingBean(value = [FeatureEngineeringModelBuildStrategy::class])
    @Bean
    fun featureEngineeringModelBuildStrategy(
        scalarTypeRegistryProvider: ObjectProvider<ScalarTypeRegistry>,
        materializationDirectiveRegistryProvider: ObjectProvider<MaterializationDirectiveRegistry>
    ): FeatureEngineeringModelBuildStrategy {
        return DefaultFeatureEngineeringModelBuildStrategy(
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

    @ConditionalOnMissingBean(value = [FeatureEngineeringModelFactory::class])
    @Bean
    fun featureEngineeringModelFactory(
        featureEngineeringModelBuildStrategy: FeatureEngineeringModelBuildStrategy
    ): FeatureEngineeringModelFactory {
        return DefaultFeatureEngineeringModelFactory(
            featureEngineeringModelBuildStrategy = featureEngineeringModelBuildStrategy
        )
    }

    @ConditionalOnMissingBean(value = [FeatureEngineeringModel::class])
    @Bean
    fun metamodel(
        featureEngineeringModelFactory: FeatureEngineeringModelFactory,
        transformerSourceProviders: ObjectProvider<TransformerSourceProvider<*>>,
        dataElementSourceProviders: ObjectProvider<DataElementSourceProvider<*>>,
        featureCalculatorProviders: ObjectProvider<FeatureCalculatorProvider<*>>,
        featureJsonValueStoreProviders: ObjectProvider<FeatureJsonValueStore>,
        featureJsonValuePublisherProviders: ObjectProvider<FeatureJsonValuePublisher>
    ): FeatureEngineeringModel {
        val builder: FeatureEngineeringModel.Builder = featureEngineeringModelFactory.builder()
        transformerSourceProviders
            .asSequence()
            .fold(builder, FeatureEngineeringModel.Builder::addTransformerSourceProvider)
        dataElementSourceProviders
            .asSequence()
            .fold(builder, FeatureEngineeringModel.Builder::addDataElementSourceProvider)
        featureCalculatorProviders
            .asSequence()
            .fold(builder, FeatureEngineeringModel.Builder::addFeatureCalculatorProvider)
        featureJsonValueStoreProviders
            .asSequence()
            .fold(builder, FeatureEngineeringModel.Builder::addFeatureJsonValueStore)
        featureJsonValuePublisherProviders
            .asSequence()
            .fold(builder, FeatureEngineeringModel.Builder::addFeatureJsonValuePublisher)
        return builder
            .build()
            .doOnError { t: Throwable ->
                logger.error(
                    "build_feature_engineering_model: [ status failed ][ type: {}, message: {} ]",
                    t::class.simpleName,
                    t.message
                )
            }
            .doOnNext { fem: FeatureEngineeringModel ->
                logger.info(
                    "build_feature_engineering_model: [ status: success ][ model.type_definition_registry.query_object_type.field_definitions.name: {} ]",
                    fem.typeDefinitionRegistry
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
