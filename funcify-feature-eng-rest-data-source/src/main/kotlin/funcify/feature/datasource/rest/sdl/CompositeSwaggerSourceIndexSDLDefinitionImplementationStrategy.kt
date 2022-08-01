package funcify.feature.datasource.rest.sdl

import arrow.core.firstOrNone
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull

/**
 *
 * @author smccarron
 * @created 2022-07-31
 */
internal class CompositeSwaggerSourceIndexSDLDefinitionImplementationStrategy(
    private val restApiDataSourceSpecificImplementationStrategies:
        List<SwaggerRestApiDataSourceIndexBasedSDLDefinitionImplementationStrategy>
) : SchematicVertexSDLDefinitionImplementationStrategy {

    override fun canBeAppliedToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Boolean {
        return restApiDataSourceSpecificImplementationStrategies.any { strategy ->
            strategy.canBeAppliedToContext(context)
        }
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        return restApiDataSourceSpecificImplementationStrategies
            .firstOrNone { strategy -> strategy.canBeAppliedToContext(context) }
            .fold({ context.successIfNonNull() }, { strategy -> strategy.applyToContext(context) })
    }
}
