package funcify.feature.datasource.rest.sdl

import funcify.feature.tools.container.attempt.Try
import graphql.language.Type

/**
 *
 * @author smccarron
 * @created 2022-07-31
 */
interface SwaggerSourceIndexSDLTypeResolutionStrategyTemplate :
    SwaggerSourceIndexSDLDefinitionImplementationTemplate<Try<Type<*>>> {}
