package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-02
 */
class CompositeSDLDefinitionImplementationStrategy(
    val sdlDefinitionImplementationStrategies:
        List<SchematicVertexSDLDefinitionImplementationStrategy>
) : SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger = loggerFor<CompositeSDLDefinitionImplementationStrategy>()
    }

    private val prioritizedStrategies:
        ImmutableList<SchematicVertexSDLDefinitionImplementationStrategy> by lazy {
        sdlDefinitionImplementationStrategies.asSequence().sorted().toPersistentList()
    }

    override fun canBeAppliedToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Boolean {
        logger.debug("can_be_applied_to_context: [ context.path: ${context.path} ]")
        return prioritizedStrategies.any { strategy -> strategy.canBeAppliedToContext(context) }
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("apply_to_context: [ context.path: ${context.path} ]")
        return prioritizedStrategies.fold(Try.success(context)) {
            ctxUpdateAttempt: Try<SchematicVertexSDLDefinitionCreationContext<*>>,
            strategy: SchematicVertexSDLDefinitionImplementationStrategy ->
            ctxUpdateAttempt.flatMap { ctx ->
                if (strategy.canBeAppliedToContext(ctx)) {
                    strategy.applyToContext(ctx)
                } else {
                    Try.success(ctx)
                }
            }
        }
    }
}
