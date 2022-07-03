package funcify.feature.datasource.sdl

import funcify.feature.schema.vertex.SourceRootVertex

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
interface SchematicVertexSDLDefinitionCombinationContextFactory {

    fun createInitialContextFromSDLDefinitionCreationContext(
        sdlDefinitionCreationContext: SchematicVertexSDLDefinitionCreationContext<*>
    ): SchematicVertexSDLDefinitionCombinationContext<SourceRootVertex>
}
