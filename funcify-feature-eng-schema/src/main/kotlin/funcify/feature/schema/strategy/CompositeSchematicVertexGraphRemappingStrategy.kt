package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
internal class CompositeSchematicVertexGraphRemappingStrategy(
    private val remappingStrategies:
        List<SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>>
) : SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext> {

    override fun canBeAppliedTo(
        context: MetamodelGraphCreationContext,
        schematicVertex: SchematicVertex
    ): Boolean {
        return remappingStrategies.any { strategy ->
            strategy.canBeAppliedTo(context, schematicVertex)
        }
    }

    override fun applyToVertexInContext(
        context: MetamodelGraphCreationContext,
        schematicVertex: SchematicVertex,
    ): Try<MetamodelGraphCreationContext> {
        return remappingStrategies.fold(context.successIfNonNull()) {
            ctxAttempt: Try<MetamodelGraphCreationContext>,
            strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext> ->
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
