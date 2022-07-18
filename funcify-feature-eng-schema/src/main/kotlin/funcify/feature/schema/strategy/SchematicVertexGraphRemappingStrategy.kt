package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexGraphRemappingStrategy {

    val remappingHandler: SchematicVertexSourceIndexBasedRemappingHandler

    fun canBeAppliedTo(
        remappingContext: SchematicVertexGraphRemappingContext,
        schematicVertex: SchematicVertex
    ): Boolean

    fun applyToVertexInContext(
        remappingContext: SchematicVertexGraphRemappingContext,
        schematicVertex: SchematicVertex,
    ): Try<SchematicVertexGraphRemappingContext> {
        return Try.attempt { remappingHandler.onSchematicVertex(schematicVertex, remappingContext) }
    }
}
