package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
internal class CompositeSchematicVertexGraphRemappingStrategy(
    private val remappingStrategies:
        PersistentList<SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>> =
        persistentListOf()
) : SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext> {

    fun addStrategy(
        remappingStrategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
    ): CompositeSchematicVertexGraphRemappingStrategy {
        return CompositeSchematicVertexGraphRemappingStrategy(
            remappingStrategies = remappingStrategies.add(remappingStrategy)
        )
    }

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
