package funcify.feature.datasource.sdl

import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-07-01
 */
interface SchematicVertexSDLDefinitionStrategy<out T : Any> {

    fun canBeAppliedToContext(context: SchematicVertexSDLDefinitionCreationContext<*>): Boolean {
        return true
    }

    fun applyToContext(context: SchematicVertexSDLDefinitionCreationContext<*>): Try<T>

}
