package funcify.feature.datasource.rest.sdl

import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.language.Type
import graphql.language.TypeName
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-01
 */
internal class DefaultSwaggerSourceIndexSDLTypeResolutionStrategy :
    SwaggerSourceIndexSDLTypeResolutionStrategyTemplate {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerSourceIndexSDLTypeResolutionStrategy>()
    }

    override fun onSourceRootSwaggerSourceContainerType(
        sourceRootVertexContext: SourceRootVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
    ): Try<Type<*>> {
        return TypeName.newTypeName(swaggerSourceContainerType.name.qualifiedForm)
            .build()
            .successIfNonNull()
    }

    override fun onSourceJunctionSwaggerContainerTypeAndAttribute(
        sourceJunctionVertexContext: SourceJunctionVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<Type<*>> {
        return SwaggerSourceAttributeSDLTypeResolver.invoke(swaggerSourceAttribute)
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """swagger_source_attribute on vertex [ 
                       |path: ${sourceJunctionVertexContext.path} ] 
                       |could not be mapped to a GraphQL SDL type: [ 
                       |source_attribute.source_path: ${swaggerSourceAttribute.sourcePath} 
                       ]""".flatten()
                )
            }
    }

    override fun onSourceLeafSwaggerAttribute(
        sourceLeafVertexContext: SourceLeafVertexSDLDefinitionCreationContext,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<Type<*>> {
        return SwaggerSourceAttributeSDLTypeResolver.invoke(swaggerSourceAttribute)
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """swagger_source_attribute on vertex [ 
                       |path: ${sourceLeafVertexContext.path} ] 
                       |could not be mapped to a GraphQL SDL type: [ 
                       |source_attribute.source_path: ${swaggerSourceAttribute.sourcePath} 
                       ]""".flatten()
                )
            }
    }

    override fun onParameterJunctionContainerTypeAndAttribute(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        swaggerParameterContainerType: SwaggerParameterContainerType,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<Type<*>> {
        return SwaggerParameterAttributeSDLTypeResolver.invoke(swaggerParameterAttribute)
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """swagger_source_attribute on vertex [ 
                       |path: ${parameterJunctionVertexContext.path} ] 
                       |could not be mapped to a GraphQL SDL type: [ 
                       |source_attribute.source_path: ${swaggerParameterAttribute.sourcePath} 
                       ]""".flatten()
                )
            }
    }

    override fun onParameterLeafSwaggerAttribute(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<Type<*>> {
        return SwaggerParameterAttributeSDLTypeResolver.invoke(swaggerParameterAttribute)
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """swagger_source_attribute on vertex [ 
                       |path: ${parameterLeafVertexContext.path} ] 
                       |could not be mapped to a GraphQL SDL type: [ 
                       |source_attribute.source_path: ${swaggerParameterAttribute.sourcePath} 
                       ]""".flatten()
                )
            }
    }
}
