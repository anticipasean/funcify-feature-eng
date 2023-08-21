package funcify.feature.materializer.model

import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-21
 */
interface MaterializationMetamodelBuildStrategy {

    fun buildMaterializationMetamodel(
        context: MaterializationMetamodelBuildContext
    ): Mono<out MaterializationMetamodel>
}
