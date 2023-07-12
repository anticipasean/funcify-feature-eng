package funcify.feature.schema

import funcify.feature.schema.context.MetamodelBuildContext
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2023-07-12
 */
interface MetamodelBuildStrategy {

    fun buildMetamodel(context: MetamodelBuildContext): Mono<out Metamodel>

}
