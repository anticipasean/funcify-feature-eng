package funcify.feature.datasource.sdl

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
fun interface SchematicVertexSDLDefinitionCreator {

    fun createSDLDefinitionForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): SchematicVertexSDLDefinitionCreationContext<*>

}
