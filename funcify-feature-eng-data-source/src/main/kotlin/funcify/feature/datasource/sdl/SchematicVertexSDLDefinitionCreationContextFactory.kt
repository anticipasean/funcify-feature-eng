package funcify.feature.datasource.sdl

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.vertex.SourceRootVertex
import graphql.language.ScalarTypeDefinition

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
interface SchematicVertexSDLDefinitionCreationContextFactory {

    fun createInitialContextForRootSchematicVertexSDLDefinition(
        metamodelGraph: MetamodelGraph,
        scalarTypeDefinitions: List<ScalarTypeDefinition> = listOf()
    ): SchematicVertexSDLDefinitionCreationContext<SourceRootVertex>
}
