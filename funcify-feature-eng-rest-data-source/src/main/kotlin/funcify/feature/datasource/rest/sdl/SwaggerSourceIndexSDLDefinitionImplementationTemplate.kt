package funcify.feature.datasource.rest.sdl

import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
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
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.reflect.KClass
import kotlin.reflect.cast
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SwaggerSourceIndexSDLDefinitionImplementationTemplate<E> {

    companion object {
        private val logger: Logger =
            loggerFor<SwaggerSourceIndexSDLDefinitionImplementationTemplate<*>>()
    }

    fun onSchematicVertexSDLDefinitionCreationContext(
        sdlDefinitionCreationContext: SchematicVertexSDLDefinitionCreationContext<*>
    ): E {
        logger.debug(
            """on_schematic_vertex_sdl_definition_creation_context: [ 
               |path: ${sdlDefinitionCreationContext.path}, 
               |current_vertex.type: ${sdlDefinitionCreationContext.currentVertex::class.simpleName} 
               |]""".flatten()
        )
        return when (sdlDefinitionCreationContext) {
            is SourceRootVertexSDLDefinitionCreationContext ->
                onSourceRootVertexContext(sdlDefinitionCreationContext)
            is SourceJunctionVertexSDLDefinitionCreationContext ->
                onSourceJunctionVertexContext(sdlDefinitionCreationContext)
            is SourceLeafVertexSDLDefinitionCreationContext ->
                onSourceLeafVertexContext(sdlDefinitionCreationContext)
            is ParameterJunctionVertexSDLDefinitionCreationContext ->
                onParameterJunctionVertexContext(sdlDefinitionCreationContext)
            is ParameterLeafVertexSDLDefinitionCreationContext ->
                onParameterLeafVertexContext(sdlDefinitionCreationContext)
        }
    }

    fun onSourceRootVertexContext(
        sourceRootVertexContext: SourceRootVertexSDLDefinitionCreationContext
    ): E {
        return onSourceRootSwaggerSourceContainerType(
            sourceRootVertexContext = sourceRootVertexContext,
            swaggerSourceContainerType =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = sourceRootVertexContext,
                    compositeIndexExtractor = SourceRootVertex::compositeContainerType,
                    sourceIndicesExtractor = { csct: CompositeSourceContainerType ->
                        csct.getSourceContainerTypeByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerSourceContainerType::class
                )
        )
    }

    fun <V : SchematicVertex, CI, SI, SWSI : Any> extractSwaggerSourceIndexFromVertexContext(
        vertexContext: SchematicVertexSDLDefinitionCreationContext<V>,
        compositeIndexExtractor: (V) -> CI,
        sourceIndicesExtractor: (CI) -> Sequence<SI>,
        swaggerSourceIndexType: KClass<out SWSI>
    ): SWSI {
        return when (
            val swaggerSourceIndex =
                sourceIndicesExtractor
                    .invoke(compositeIndexExtractor.invoke(vertexContext.currentVertex))
                    .filter { si -> swaggerSourceIndexType.isInstance(si) }
                    .map { si -> swaggerSourceIndexType.cast(si) }
                    .firstOrNull()
        ) {
            null -> {
                val sourceIndexTypesAvailable: String =
                    sourceIndicesExtractor
                        .invoke(compositeIndexExtractor.invoke(vertexContext.currentVertex))
                        .map { si -> si!!::class.qualifiedName }
                        .joinToString(", ", "{ ", " }")
                throw RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """did not find a swagger_source_index 
                        |in ${vertexContext.currentVertex::class.simpleName}: 
                        |[ actual: $sourceIndexTypesAvailable, 
                        |expected: ${swaggerSourceIndexType.qualifiedName} 
                        |]""".flatten()
                )
            }
            else -> swaggerSourceIndex
        }
    }

    fun onSourceRootSwaggerSourceContainerType(
        sourceRootVertexContext: SourceRootVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType
    ): E

    fun onSourceJunctionVertexContext(
        sourceJunctionVertexContext: SourceJunctionVertexSDLDefinitionCreationContext
    ): E {
        return onSourceJunctionSwaggerContainerTypeAndAttribute(
            sourceJunctionVertexContext = sourceJunctionVertexContext,
            swaggerSourceContainerType =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = sourceJunctionVertexContext,
                    compositeIndexExtractor = SourceJunctionVertex::compositeContainerType,
                    sourceIndicesExtractor = { cct ->
                        cct.getSourceContainerTypeByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerSourceContainerType::class
                ),
            swaggerSourceAttribute =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = sourceJunctionVertexContext,
                    compositeIndexExtractor = SourceJunctionVertex::compositeAttribute,
                    sourceIndicesExtractor = { ca ->
                        ca.getSourceAttributeByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerSourceAttribute::class
                )
        )
    }

    fun onSourceJunctionSwaggerContainerTypeAndAttribute(
        sourceJunctionVertexContext: SourceJunctionVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
        swaggerSourceAttribute: SwaggerSourceAttribute
    ): E

    fun onSourceLeafVertexContext(
        sourceLeafVertexContext: SourceLeafVertexSDLDefinitionCreationContext
    ): E {
        return onSourceLeafSwaggerAttribute(
            sourceLeafVertexContext = sourceLeafVertexContext,
            swaggerSourceAttribute =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = sourceLeafVertexContext,
                    compositeIndexExtractor = SourceLeafVertex::compositeAttribute,
                    sourceIndicesExtractor = { ca ->
                        ca.getSourceAttributeByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerSourceAttribute::class
                )
        )
    }

    fun onSourceLeafSwaggerAttribute(
        sourceLeafVertexContext: SourceLeafVertexSDLDefinitionCreationContext,
        swaggerSourceAttribute: SwaggerSourceAttribute
    ): E

    fun onParameterJunctionVertexContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext
    ): E {
        return onParameterJunctionContainerTypeAndAttribute(
            parameterJunctionVertexContext = parameterJunctionVertexContext,
            swaggerParameterContainerType =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = parameterJunctionVertexContext,
                    compositeIndexExtractor =
                        ParameterJunctionVertex::compositeParameterContainerType,
                    sourceIndicesExtractor = { cpct ->
                        cpct.getParameterContainerTypeByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerParameterContainerType::class
                ),
            swaggerParameterAttribute =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = parameterJunctionVertexContext,
                    compositeIndexExtractor = ParameterJunctionVertex::compositeParameterAttribute,
                    sourceIndicesExtractor = { cpct ->
                        cpct.getParameterAttributesByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerParameterAttribute::class
                )
        )
    }

    fun onParameterJunctionContainerTypeAndAttribute(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        swaggerParameterContainerType: SwaggerParameterContainerType,
        swaggerParameterAttribute: SwaggerParameterAttribute
    ): E

    fun onParameterLeafVertexContext(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext
    ): E {
        return onParameterLeafSwaggerAttribute(
            parameterLeafVertexContext = parameterLeafVertexContext,
            swaggerParameterAttribute =
                extractSwaggerSourceIndexFromVertexContext(
                    vertexContext = parameterLeafVertexContext,
                    compositeIndexExtractor = ParameterLeafVertex::compositeParameterAttribute,
                    sourceIndicesExtractor = { cpa ->
                        cpa.getParameterAttributesByDataSource().values.asSequence()
                    },
                    swaggerSourceIndexType = SwaggerParameterAttribute::class
                )
        )
    }

    fun onParameterLeafSwaggerAttribute(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext,
        swaggerParameterAttribute: SwaggerParameterAttribute
    ): E
}
