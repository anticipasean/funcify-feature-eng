package funcify.feature.file

/**
 * @author smccarron
 * @created 2023-08-16
 */
fun interface FileRegistryFeatureCalculatorProviderFactory {

    fun builder(): FileRegistryFeatureCalculatorProvider.Builder
}
