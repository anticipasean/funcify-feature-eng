package funcify.feature.datasource.sdl

import funcify.feature.tools.container.attempt.Try
import graphql.language.Type

/**
 *
 * @author smccarron
 * @created 2022-06-25
 */
fun interface SchematicVertexSDLDefinitionTypeStrategy :
    Comparable<SchematicVertexSDLDefinitionTypeStrategy> {

    companion object {
        const val HIGHEST_PRIORITY: Int = Int.MIN_VALUE
        const val DEFAULT_PRIORITY: Int = 0
        const val LOWEST_PRIORITY: Int = Int.MAX_VALUE
    }

    /**
     * The lower the priority, the later this strategy _would_ be applied in the context of multiple
     * naming strategies being available. If all strategies use the #DEFAULT_PRIORITY, then
     * strategies will be applied in whatever order Spring populates the composite strategy
     */
    override fun compareTo(other: SchematicVertexSDLDefinitionTypeStrategy): Int {
        return DEFAULT_PRIORITY
    }

    /**
     * If this strategy fails to determine a name for the vertex within the context (=> results in a
     * Try.Failure), the strategy will be used
     */
    fun determineSDLTypeForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<Type<*>>
}
