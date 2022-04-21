package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph

/**
 *
 * @author smccarron
 * @created 4/3/22
 */
interface MetamodelGraphFactory {

    fun builder(): MetamodelGraph.Builder

}
