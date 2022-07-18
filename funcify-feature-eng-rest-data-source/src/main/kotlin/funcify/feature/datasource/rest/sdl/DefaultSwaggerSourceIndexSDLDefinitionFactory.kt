package funcify.feature.datasource.rest.sdl

import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
internal class DefaultSwaggerSourceIndexSDLDefinitionFactory :
    SwaggerSourceIndexSDLDefinitionImplementationTemplate<
        Try<SchematicVertexSDLDefinitionCreationContext<*>>> {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerSourceIndexSDLDefinitionFactory>()
    }

    override fun onSourceRootSwaggerSourceContainerType(
        sourceRootVertexContext: SourceRootVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_source_root_swagger_source_container_type: [ path: ${sourceRootVertexContext.path} ]"
        )
        TODO("Not yet implemented")
    }

    override fun onSourceJunctionSwaggerContainerTypeAndAttribute(
        sourceJunctionVertexContext: SourceJunctionVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_source_junction_swagger_source_container_type_and_attribute: [ path: ${sourceJunctionVertexContext.path} ]"
        )
        TODO("Not yet implemented")
    }

    override fun onSourceLeafSwaggerAttribute(
        sourceLeafVertexContext: SourceLeafVertexSDLDefinitionCreationContext,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("on_source_leaf_swagger_attribute: [ path: ${sourceLeafVertexContext.path} ]")
        TODO("Not yet implemented")
    }

    override fun onParameterJunctionContainerTypeAndAttribute(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        swaggerParameterContainerType: SwaggerParameterContainerType,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_parameter_junction_container_type_and_attribute: [ path: ${parameterJunctionVertexContext.path} ]"
        )
        TODO("Not yet implemented")
    }

    override fun onParameterLeafSwaggerAttribute(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_parameter_leaf_swagger_attribute: [ path: ${parameterLeafVertexContext.path} ]"
        )
        TODO("Not yet implemented")
    }
}
