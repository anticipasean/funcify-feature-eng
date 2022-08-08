package funcify.feature.datasource.rest.sdl

import arrow.core.filterIsInstance
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
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.language.FieldDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputValueDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.Type
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
internal class DefaultSwaggerSourceIndexSDLDefinitionFactory(
    private val swaggerSourceIndexSDLTypeResolutionStrategy:
        SwaggerSourceIndexSDLTypeResolutionStrategyTemplate
) :
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
        return if (sourceRootVertexContext.existingObjectTypeDefinition.isDefined()) {
            sourceRootVertexContext.successIfNonNull()
        } else {
            sourceRootVertexContext
                .update {
                    addSDLDefinitionForSchematicPath(
                        sourceRootVertexContext.path,
                        ObjectTypeDefinition.newObjectTypeDefinition()
                            .name(
                                sourceRootVertexContext.currentVertex.compositeContainerType
                                    .conventionalName
                                    .qualifiedForm
                            )
                            .build()
                    )
                }
                .successIfNonNull()
        }
    }

    override fun onSourceJunctionSwaggerContainerTypeAndAttribute(
        sourceJunctionVertexContext: SourceJunctionVertexSDLDefinitionCreationContext,
        swaggerSourceContainerType: SwaggerSourceContainerType,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_source_junction_swagger_source_container_type_and_attribute: [ path: ${sourceJunctionVertexContext.path} ]"
        )
        return when {
            sourceJunctionVertexContext.existingObjectTypeDefinition.isDefined() &&
                sourceJunctionVertexContext.existingFieldDefinition.isDefined() -> {
                sourceJunctionVertexContext.successIfNonNull()
            }
            sourceJunctionVertexContext.existingObjectTypeDefinition.isDefined() -> {
                createFieldDefinitionForSwaggerSourceAttribute(
                        swaggerSourceIndexSDLTypeResolutionStrategy
                            .onSourceJunctionSwaggerContainerTypeAndAttribute(
                                sourceJunctionVertexContext,
                                swaggerSourceContainerType,
                                swaggerSourceAttribute
                            ),
                        sourceJunctionVertexContext.path,
                        sourceJunctionVertexContext.compositeSourceAttribute.conventionalName,
                        swaggerSourceAttribute
                    )
                    .map { fieldDef ->
                        sourceJunctionVertexContext.update {
                            addSDLDefinitionForSchematicPath(
                                sourceJunctionVertexContext.path,
                                fieldDef
                            )
                        }
                    }
            }
            else -> {
                ObjectTypeDefinition.newObjectTypeDefinition()
                    .name(
                        sourceJunctionVertexContext.currentVertex.compositeContainerType
                            .conventionalName
                            .qualifiedForm
                    )
                    .build()
                    .successIfNonNull()
                    .zip(
                        createFieldDefinitionForSwaggerSourceAttribute(
                            swaggerSourceIndexSDLTypeResolutionStrategy
                                .onSourceJunctionSwaggerContainerTypeAndAttribute(
                                    sourceJunctionVertexContext,
                                    swaggerSourceContainerType,
                                    swaggerSourceAttribute
                                ),
                            sourceJunctionVertexContext.path,
                            sourceJunctionVertexContext.compositeSourceAttribute.conventionalName,
                            swaggerSourceAttribute
                        )
                    )
                    .map { (objTypeDef, fieldDef) ->
                        sourceJunctionVertexContext.update {
                            addSDLDefinitionForSchematicPath(
                                sourceJunctionVertexContext.path,
                                objTypeDef
                            )
                            addSDLDefinitionForSchematicPath(
                                sourceJunctionVertexContext.path,
                                fieldDef
                            )
                        }
                    }
            }
        }
    }

    private fun createFieldDefinitionForSwaggerSourceAttribute(
        resolvedSDLType: Try<Type<*>>,
        currentSchematicPath: SchematicPath,
        currentVertexName: ConventionalName,
        swaggerSourceAttribute: SwaggerSourceAttribute
    ): Try<FieldDefinition> {
        return resolvedSDLType.map { sdlType ->
            FieldDefinition.newFieldDefinition()
                .name( // Use name on current_vertex in case it was intentionally changed
                    // during remapping strategies to fit the camel_case convention
                    currentVertexName.qualifiedForm
                )
                .type(sdlType)
                .build()
        }
    }

    override fun onSourceLeafSwaggerAttribute(
        sourceLeafVertexContext: SourceLeafVertexSDLDefinitionCreationContext,
        swaggerSourceAttribute: SwaggerSourceAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("on_source_leaf_swagger_attribute: [ path: ${sourceLeafVertexContext.path} ]")
        if (sourceLeafVertexContext.existingFieldDefinition.isDefined()) {
            return sourceLeafVertexContext.successIfNonNull()
        }
        return createFieldDefinitionForSwaggerSourceAttribute(
                swaggerSourceIndexSDLTypeResolutionStrategy.onSourceLeafSwaggerAttribute(
                    sourceLeafVertexContext,
                    swaggerSourceAttribute
                ),
                sourceLeafVertexContext.path,
                sourceLeafVertexContext.compositeSourceAttribute.conventionalName,
                swaggerSourceAttribute
            )
            .map { fieldDef ->
                sourceLeafVertexContext.update {
                    addSDLDefinitionForSchematicPath(sourceLeafVertexContext.path, fieldDef)
                }
            }
    }

    override fun onParameterJunctionContainerTypeAndAttribute(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        swaggerParameterContainerType: SwaggerParameterContainerType,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_parameter_junction_container_type_and_attribute: [ path: ${parameterJunctionVertexContext.path} ]"
        )
        return when {
            parameterJunctionVertexContext.parentVertex
                .filterIsInstance<SourceAttributeVertex>()
                .isDefined() -> {
                if (parameterJunctionVertexContext.existingArgumentDefinition.isDefined()) {
                    parameterJunctionVertexContext.successIfNonNull()
                } else {
                    InputObjectTypeDefinition.newInputObjectDefinition()
                        .name(swaggerParameterContainerType.name.qualifiedForm)
                        .build()
                        .successIfNonNull()
                        .zip(
                            createFieldArgumentInputValueDefinitionForSwaggerParameterAttribute(
                                swaggerSourceIndexSDLTypeResolutionStrategy
                                    .onParameterJunctionContainerTypeAndAttribute(
                                        parameterJunctionVertexContext,
                                        swaggerParameterContainerType,
                                        swaggerParameterAttribute
                                    ),
                                parameterJunctionVertexContext.path,
                                parameterJunctionVertexContext.compositeParameterAttribute
                                    .conventionalName,
                                swaggerParameterAttribute
                            )
                        )
                        .map { (inputObjectTypeDef, inputValueDef) ->
                            parameterJunctionVertexContext.update {
                                addSDLDefinitionForSchematicPath(
                                    parameterJunctionVertexContext.path,
                                    inputObjectTypeDef
                                )
                                addSDLDefinitionForSchematicPath(
                                    parameterJunctionVertexContext.path,
                                    inputValueDef
                                )
                            }
                        }
                }
            }
            else -> {
                RestApiDataSourceException(
                        RestApiErrorResponse.UNEXPECTED_ERROR,
                        """unhandled parameter_junction_vertex 
                        |sdl_definition_creation for [ path: ${parameterJunctionVertexContext.path}, 
                        |swagger_parameter_attribute.name: ${swaggerParameterAttribute.name} 
                        |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    private fun createFieldArgumentInputValueDefinitionForSwaggerParameterAttribute(
        resolvedSDLType: Try<Type<*>>,
        schematicPath: SchematicPath,
        parameterAttributeVertexName: ConventionalName,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<InputValueDefinition> {
        return resolvedSDLType.map { sdlType ->
            InputValueDefinition.newInputValueDefinition()
                .name(parameterAttributeVertexName.qualifiedForm)
                .type(sdlType)
                .build()
        }
    }

    override fun onParameterLeafSwaggerAttribute(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext,
        swaggerParameterAttribute: SwaggerParameterAttribute,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "on_parameter_leaf_swagger_attribute: [ path: ${parameterLeafVertexContext.path} ]"
        )
        return when {
            parameterLeafVertexContext.parentVertex
                .filterIsInstance<SourceAttributeVertex>()
                .isDefined() -> {
                if (parameterLeafVertexContext.existingInputValueDefinition.isDefined()) {
                    parameterLeafVertexContext.successIfNonNull()
                } else {
                    createFieldArgumentInputValueDefinitionForSwaggerParameterAttribute(
                            swaggerSourceIndexSDLTypeResolutionStrategy
                                .onParameterLeafSwaggerAttribute(
                                    parameterLeafVertexContext,
                                    swaggerParameterAttribute
                                ),
                            parameterLeafVertexContext.path,
                            parameterLeafVertexContext.compositeParameterAttribute.conventionalName,
                            swaggerParameterAttribute
                        )
                        .map { inputValueDef ->
                            parameterLeafVertexContext.update {
                                addSDLDefinitionForSchematicPath(
                                    parameterLeafVertexContext.path,
                                    inputValueDef
                                )
                            }
                        }
                }
            }
            else -> {
                RestApiDataSourceException(
                        RestApiErrorResponse.UNEXPECTED_ERROR,
                        """unhandled parameter_leaf_vertex  
                        |sdl_definition_creation for [ path: ${parameterLeafVertexContext.path}, 
                        |swagger_parameter_attribute.name: ${swaggerParameterAttribute.name} 
                        |]""".flatten()
                    )
                    .failure()
            }
        }
    }
}
