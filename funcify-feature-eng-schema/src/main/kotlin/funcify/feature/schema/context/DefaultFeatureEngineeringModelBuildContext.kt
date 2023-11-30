package funcify.feature.schema.context

import funcify.feature.schema.context.FeatureEngineeringModelBuildContext.Builder
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributeRegistry
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultFeatureEngineeringModelBuildContext(
    override val transformerSourceProviders: PersistentList<TransformerSourceProvider<*>>,
    override val dataElementSourceProviders: PersistentList<DataElementSourceProvider<*>>,
    override val featureCalculatorProviders: PersistentList<FeatureCalculatorProvider<*>>,
    override val featureJsonValueStoresByName: PersistentMap<String, FeatureJsonValueStore>,
    override val featureJsonValuePublishersByName: PersistentMap<String, FeatureJsonValuePublisher>,
    override val transformerSourceProvidersByName:
        PersistentMap<String, TransformerSourceProvider<*>>,
    override val dataElementSourceProvidersByName:
        PersistentMap<String, DataElementSourceProvider<*>>,
    override val featureCalculatorProvidersByName:
        PersistentMap<String, FeatureCalculatorProvider<*>>,
    override val transformerSourcesByName: PersistentMap<String, TransformerSource>,
    override val dataElementSourcesByName: PersistentMap<String, DataElementSource>,
    override val featureCalculatorsByName: PersistentMap<String, FeatureCalculator>,
    override val typeDefinitionRegistry: TypeDefinitionRegistry,
    override val entityRegistry: EntityRegistry,
    override val lastUpdatedTemporalAttributeRegistry: LastUpdatedTemporalAttributeRegistry,
) : FeatureEngineeringModelBuildContext {

    companion object {

        fun empty(): DefaultFeatureEngineeringModelBuildContext {
            return DefaultFeatureEngineeringModelBuildContext(
                transformerSourceProviders = persistentListOf(),
                dataElementSourceProviders = persistentListOf(),
                featureCalculatorProviders = persistentListOf(),
                featureJsonValueStoresByName = persistentMapOf(),
                featureJsonValuePublishersByName = persistentMapOf(),
                transformerSourceProvidersByName = persistentMapOf(),
                dataElementSourceProvidersByName = persistentMapOf(),
                featureCalculatorProvidersByName = persistentMapOf(),
                transformerSourcesByName = persistentMapOf(),
                dataElementSourcesByName = persistentMapOf(),
                featureCalculatorsByName = persistentMapOf(),
                typeDefinitionRegistry = TypeDefinitionRegistry(),
                entityRegistry = EntityRegistry.newRegistry(),
                lastUpdatedTemporalAttributeRegistry =
                    LastUpdatedTemporalAttributeRegistry.newRegistry()
            )
        }

        internal class DefaultBuilder(
            private val dataElementSourceProviders:
                PersistentList.Builder<DataElementSourceProvider<*>>,
            private val transformerSourceProviders:
                PersistentList.Builder<TransformerSourceProvider<*>>,
            private val featureCalculatorProviders:
                PersistentList.Builder<FeatureCalculatorProvider<*>>,
            private val featureJsonValueStoresByName:
                PersistentMap.Builder<String, FeatureJsonValueStore>,
            private val featureJsonValuePublishersByName:
                PersistentMap.Builder<String, FeatureJsonValuePublisher>,
            private val transformerSourceProvidersByName:
                PersistentMap.Builder<String, TransformerSourceProvider<*>>,
            private val dataElementSourceProvidersByName:
                PersistentMap.Builder<String, DataElementSourceProvider<*>>,
            private val featureCalculatorProvidersByName:
                PersistentMap.Builder<String, FeatureCalculatorProvider<*>>,
            private val transformerSourcesByName: PersistentMap.Builder<String, TransformerSource>,
            private val dataElementSourcesByName: PersistentMap.Builder<String, DataElementSource>,
            private val featureCalculatorsByName: PersistentMap.Builder<String, FeatureCalculator>,
            private var typeDefinitionRegistry: TypeDefinitionRegistry,
            private var entityRegistry: EntityRegistry,
            private var lastUpdatedTemporalAttributeRegistry: LastUpdatedTemporalAttributeRegistry,
        ) : Builder {

            override fun addTransformerSourceProvider(
                transformerSourceProvider: TransformerSourceProvider<*>
            ): Builder {
                this.transformerSourceProviders.add(transformerSourceProvider)
                this.transformerSourceProvidersByName.put(
                    transformerSourceProvider.name,
                    transformerSourceProvider
                )
                return this
            }

            override fun addDataElementSourceProvider(
                dataElementSourceProvider: DataElementSourceProvider<*>
            ): Builder {
                this.dataElementSourceProviders.add(dataElementSourceProvider)
                this.dataElementSourceProvidersByName.put(
                    dataElementSourceProvider.name,
                    dataElementSourceProvider
                )
                return this
            }

            override fun addFeatureCalculatorProvider(
                featureCalculatorProvider: FeatureCalculatorProvider<*>
            ): Builder {
                this.featureCalculatorProviders.add(featureCalculatorProvider)
                this.featureCalculatorProvidersByName.put(
                    featureCalculatorProvider.name,
                    featureCalculatorProvider
                )
                return this
            }

            override fun addTransformerSource(transformerSource: TransformerSource): Builder {
                this.transformerSourcesByName.put(transformerSource.name, transformerSource)
                return this
            }

            override fun addDataElementSource(dataElementSource: DataElementSource): Builder {
                this.dataElementSourcesByName.put(dataElementSource.name, dataElementSource)
                return this
            }

            override fun addFeatureCalculator(featureCalculator: FeatureCalculator): Builder {
                this.featureCalculatorsByName.put(featureCalculator.name, featureCalculator)
                return this
            }

            override fun typeDefinitionRegistry(
                typeDefinitionRegistry: TypeDefinitionRegistry
            ): Builder {
                this.typeDefinitionRegistry = typeDefinitionRegistry
                return this
            }

            override fun addFeatureJsonValueStore(
                featureJsonValueStore: FeatureJsonValueStore
            ): Builder {
                this.featureJsonValueStoresByName.put(
                    featureJsonValueStore.name,
                    featureJsonValueStore
                )
                return this
            }

            override fun addFeatureJsonValuePublisher(
                featureJsonValuePublisher: FeatureJsonValuePublisher
            ): Builder {
                this.featureJsonValuePublishersByName.put(
                    featureJsonValuePublisher.name,
                    featureJsonValuePublisher
                )
                return this
            }

            override fun entityRegistry(entityRegistry: EntityRegistry): Builder {
                this.entityRegistry = entityRegistry
                return this
            }

            override fun lastUpdatedTemporalAttributePathRegistry(
                lastUpdatedTemporalAttributeRegistry: LastUpdatedTemporalAttributeRegistry
            ): Builder {
                this.lastUpdatedTemporalAttributeRegistry = lastUpdatedTemporalAttributeRegistry
                return this
            }

            override fun build(): FeatureEngineeringModelBuildContext {
                return DefaultFeatureEngineeringModelBuildContext(
                    transformerSourceProviders = transformerSourceProviders.build(),
                    dataElementSourceProviders = dataElementSourceProviders.build(),
                    featureCalculatorProviders = featureCalculatorProviders.build(),
                    featureJsonValueStoresByName = featureJsonValueStoresByName.build(),
                    featureJsonValuePublishersByName = featureJsonValuePublishersByName.build(),
                    transformerSourceProvidersByName = transformerSourceProvidersByName.build(),
                    dataElementSourceProvidersByName = dataElementSourceProvidersByName.build(),
                    featureCalculatorProvidersByName = featureCalculatorProvidersByName.build(),
                    transformerSourcesByName = transformerSourcesByName.build(),
                    dataElementSourcesByName = dataElementSourcesByName.build(),
                    featureCalculatorsByName = featureCalculatorsByName.build(),
                    typeDefinitionRegistry = typeDefinitionRegistry,
                    entityRegistry = entityRegistry,
                    lastUpdatedTemporalAttributeRegistry = lastUpdatedTemporalAttributeRegistry
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): FeatureEngineeringModelBuildContext {
        return transformer
            .invoke(
                DefaultBuilder(
                    transformerSourceProviders = this.transformerSourceProviders.builder(),
                    dataElementSourceProviders = this.dataElementSourceProviders.builder(),
                    featureCalculatorProviders = this.featureCalculatorProviders.builder(),
                    featureJsonValueStoresByName = this.featureJsonValueStoresByName.builder(),
                    featureJsonValuePublishersByName =
                        this.featureJsonValuePublishersByName.builder(),
                    transformerSourceProvidersByName =
                        this.transformerSourceProvidersByName.builder(),
                    dataElementSourceProvidersByName =
                        this.dataElementSourceProvidersByName.builder(),
                    featureCalculatorProvidersByName =
                        this.featureCalculatorProvidersByName.builder(),
                    transformerSourcesByName = this.transformerSourcesByName.builder(),
                    dataElementSourcesByName = this.dataElementSourcesByName.builder(),
                    featureCalculatorsByName = this.featureCalculatorsByName.builder(),
                    typeDefinitionRegistry = this.typeDefinitionRegistry,
                    entityRegistry = this.entityRegistry,
                    lastUpdatedTemporalAttributeRegistry = this.lastUpdatedTemporalAttributeRegistry
                )
            )
            .build()
    }
}
