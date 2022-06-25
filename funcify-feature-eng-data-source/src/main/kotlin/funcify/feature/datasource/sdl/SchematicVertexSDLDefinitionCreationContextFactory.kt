package funcify.feature.datasource.sdl

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.vertex.SourceRootVertex

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
interface SchematicVertexSDLDefinitionCreationContextFactory {

    fun createInitialContextForRootSchematicVertexSDLDefinition(
        metamodelGraph: MetamodelGraph
    ): SchematicVertexSDLDefinitionCreationContext<SourceRootVertex>

}
