package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexGraphRemappingStrategy<C> {

    fun canBeAppliedTo(context: C, schematicVertex: SchematicVertex): Boolean

    fun applyToVertexInContext(
        context: C,
        schematicVertex: SchematicVertex,
    ): Try<C>
}
