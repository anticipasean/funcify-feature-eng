package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
internal class CompositeSchematicVertexGraphRemappingStrategy(
    private val remappingStrategies: List<SchematicVertexGraphRemappingStrategy>
) : SchematicVertexGraphRemappingStrategy {

    override fun canBeAppliedTo(
        remappingContext: SchematicVertexGraphRemappingContext,
        schematicVertex: SchematicVertex
    ): Boolean {
        return remappingStrategies.any { strategy ->
            strategy.canBeAppliedTo(remappingContext, schematicVertex)
        }
    }

    override fun applyToVertexInContext(
        remappingContext: SchematicVertexGraphRemappingContext,
        schematicVertex: SchematicVertex,
    ): Try<SchematicVertexGraphRemappingContext> {
        return remappingStrategies.fold(remappingContext.successIfNonNull()) {
            ctxAttempt: Try<SchematicVertexGraphRemappingContext>,
            strategy: SchematicVertexGraphRemappingStrategy ->
            ctxAttempt.flatMap { ctx ->
                if (strategy.canBeAppliedTo(ctx, schematicVertex)) {
                    strategy.applyToVertexInContext(ctx, schematicVertex)
                } else {
                    ctx.successIfNonNull()
                }
            }
        }
    }
}
