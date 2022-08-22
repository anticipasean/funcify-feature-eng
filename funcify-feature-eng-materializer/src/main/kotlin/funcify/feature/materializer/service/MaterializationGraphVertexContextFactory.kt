package funcify.feature.materializer.service

import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.vertex.SourceRootVertex

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
interface MaterializationGraphVertexContextFactory {

    fun createSourceRootVertexContext(
        sourceRootVertex: SourceRootVertex,
        metamodelGraph: MetamodelGraph
    ): SourceRootMaterializationGraphVertexContext
}
