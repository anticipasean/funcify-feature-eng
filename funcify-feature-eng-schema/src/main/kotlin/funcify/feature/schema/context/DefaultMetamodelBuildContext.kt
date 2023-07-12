package funcify.feature.schema.context

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultMetamodelBuildContext(
    override val transformerSourceProviders: PersistentList<TransformerSourceProvider<*>> =
        persistentListOf(),
    override val dataElementSourceProviders: PersistentList<DataElementSourceProvider<*>> =
        persistentListOf(),
    override val featureCalculatorProviders: PersistentList<FeatureCalculatorProvider<*>> =
        persistentListOf(),
    override val transformerSourcesByName: PersistentMap<String, TransformerSource> =
        persistentMapOf(),
    override val dataElementSourcesByName: PersistentMap<String, DataElementSource> =
        persistentMapOf(),
    override val featureCalculatorsByName: PersistentMap<String, FeatureCalculator> =
        persistentMapOf(),
) : MetamodelBuildContext {

    companion object {

        internal class DefaultBuilder(
            private val dataElementSourceProviders:
                PersistentList.Builder<DataElementSourceProvider<*>>,
            private val transformerSourceProviders:
                PersistentList.Builder<TransformerSourceProvider<*>>,
            private val featureCalculatorProviders:
                PersistentList.Builder<FeatureCalculatorProvider<*>>,
            private val transformerSourcesByName: PersistentMap.Builder<String, TransformerSource>,
            private val dataElementSourcesByName: PersistentMap.Builder<String, DataElementSource>,
            private val featureCalculatorsByName: PersistentMap.Builder<String, FeatureCalculator>,
        ) : MetamodelBuildContext.Builder {

            override fun addTransformerSourceProvider(
                transformerSourceProvider: TransformerSourceProvider<*>
            ): MetamodelBuildContext.Builder {
                transformerSourceProviders.add(transformerSourceProvider)
                return this
            }

            override fun addDataElementSourceProvider(
                dataElementSourceProvider: DataElementSourceProvider<*>
            ): MetamodelBuildContext.Builder {
                this.dataElementSourceProviders.add(dataElementSourceProvider)
                return this
            }

            override fun addFeatureCalculatorProvider(
                featureCalculatorProvider: FeatureCalculatorProvider<*>
            ): MetamodelBuildContext.Builder {
                this.featureCalculatorProviders.add(featureCalculatorProvider)
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

            override fun build(): MetamodelBuildContext {
                return DefaultMetamodelBuildContext(
                    transformerSourceProviders = transformerSourceProviders.build(),
                    dataElementSourceProviders = dataElementSourceProviders.build(),
                    featureCalculatorProviders = featureCalculatorProviders.build(),
                    transformerSourcesByName = transformerSourcesByName.build(),
                    dataElementSourcesByName = dataElementSourcesByName.build(),
                    featureCalculatorsByName = featureCalculatorsByName.build(),
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
                    transformerSourcesByName = this.transformerSourcesByName.builder(),
                    dataElementSourcesByName = this.dataElementSourcesByName.builder(),
                    featureCalculatorsByName = this.featureCalculatorsByName.builder()
                )
            )
            .build()
    }
}
