package funcify.feature.schema.context

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultMetamodelBuildContext(
    override val transformerSourceProviders: PersistentList<TransformerSourceProvider<*>>,
    override val dataElementSourceProviders: PersistentList<DataElementSourceProvider<*>>,
    override val featureCalculatorProviders: PersistentList<FeatureCalculatorProvider<*>>,
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
) : MetamodelBuildContext {

    companion object {

        fun empty(): DefaultMetamodelBuildContext {
            return DefaultMetamodelBuildContext(
                transformerSourceProviders = persistentListOf(),
                dataElementSourceProviders = persistentListOf(),
                featureCalculatorProviders = persistentListOf(),
                transformerSourceProvidersByName = persistentMapOf(),
                dataElementSourceProvidersByName = persistentMapOf(),
                featureCalculatorProvidersByName = persistentMapOf(),
                transformerSourcesByName = persistentMapOf(),
                dataElementSourcesByName = persistentMapOf(),
                featureCalculatorsByName = persistentMapOf(),
                typeDefinitionRegistry = TypeDefinitionRegistry()
            )
        }

        internal class DefaultBuilder(
            private val dataElementSourceProviders:
                PersistentList.Builder<DataElementSourceProvider<*>>,
            private val transformerSourceProviders:
                PersistentList.Builder<TransformerSourceProvider<*>>,
            private val featureCalculatorProviders:
                PersistentList.Builder<FeatureCalculatorProvider<*>>,
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
        ) : MetamodelBuildContext.Builder {

            override fun addTransformerSourceProvider(
                transformerSourceProvider: TransformerSourceProvider<*>
            ): MetamodelBuildContext.Builder {
                this.transformerSourceProviders.add(transformerSourceProvider)
                this.transformerSourceProvidersByName.put(
                    transformerSourceProvider.name,
                    transformerSourceProvider
                )
                return this
            }

            override fun addDataElementSourceProvider(
                dataElementSourceProvider: DataElementSourceProvider<*>
            ): MetamodelBuildContext.Builder {
                this.dataElementSourceProviders.add(dataElementSourceProvider)
                this.dataElementSourceProvidersByName.put(
                    dataElementSourceProvider.name,
                    dataElementSourceProvider
                )
                return this
            }

            override fun addFeatureCalculatorProvider(
                featureCalculatorProvider: FeatureCalculatorProvider<*>
            ): MetamodelBuildContext.Builder {
                this.featureCalculatorProviders.add(featureCalculatorProvider)
                this.featureCalculatorProvidersByName.put(
                    featureCalculatorProvider.name,
                    featureCalculatorProvider
                )
                return this
            }

            override fun addTransformerSource(
                transformerSource: TransformerSource
            ): MetamodelBuildContext.Builder {
                this.transformerSourcesByName.put(transformerSource.name, transformerSource)
                return this
            }

            override fun addDataElementSource(
                dataElementSource: DataElementSource
            ): MetamodelBuildContext.Builder {
                this.dataElementSourcesByName.put(dataElementSource.name, dataElementSource)
                return this
            }

            override fun addFeatureCalculator(
                featureCalculator: FeatureCalculator
            ): MetamodelBuildContext.Builder {
                this.featureCalculatorsByName.put(featureCalculator.name, featureCalculator)
                return this
            }

            override fun typeDefinitionRegistry(
                typeDefinitionRegistry: TypeDefinitionRegistry
            ): MetamodelBuildContext.Builder {
                this.typeDefinitionRegistry = typeDefinitionRegistry
                return this
            }

            override fun build(): MetamodelBuildContext {
                return DefaultMetamodelBuildContext(
                    transformerSourceProviders = transformerSourceProviders.build(),
                    dataElementSourceProviders = dataElementSourceProviders.build(),
                    featureCalculatorProviders = featureCalculatorProviders.build(),
                    transformerSourceProvidersByName = transformerSourceProvidersByName.build(),
                    dataElementSourceProvidersByName = dataElementSourceProvidersByName.build(),
                    featureCalculatorProvidersByName = featureCalculatorProvidersByName.build(),
                    transformerSourcesByName = transformerSourcesByName.build(),
                    dataElementSourcesByName = dataElementSourcesByName.build(),
                    featureCalculatorsByName = featureCalculatorsByName.build(),
                    typeDefinitionRegistry = typeDefinitionRegistry
                )
            }
        }
    }

    override fun update(
        transformer: MetamodelBuildContext.Builder.() -> MetamodelBuildContext.Builder
    ): MetamodelBuildContext {
        return transformer
            .invoke(
                DefaultBuilder(
                    transformerSourceProviders = this.transformerSourceProviders.builder(),
                    dataElementSourceProviders = this.dataElementSourceProviders.builder(),
                    featureCalculatorProviders = this.featureCalculatorProviders.builder(),
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
                )
            )
            .build()
    }
}
