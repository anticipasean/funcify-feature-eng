package funcify.feature.materializer.model

/**
 * @author smccarron
 * @created 2023-08-21
 */
interface MaterializationMetamodelFactory {

    fun builder(): MaterializationMetamodel.Builder
}
