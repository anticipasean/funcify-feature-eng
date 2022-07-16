package funcify.feature.datasource.graphql.sdl

import arrow.core.identity
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLParameterAttribute
import funcify.feature.datasource.graphql.schema.GraphQLParameterContainerType
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.execution.ValuesResolver
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputValueDefinition
import graphql.language.Value
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectField
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-15
 */
interface GraphQLParameterIndexSDLDefinitionCreationTemplate {

    companion object {
        private val logger: Logger = loggerFor<GraphQLParameterIndexSDLDefinitionCreationTemplate>()
    }

    fun createParameterSDLDefinitionForParameterJunctionVertexInContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "create_parameter_sdl_definition_for_parameter_junction_vertex_in_context: [ path: ${parameterJunctionVertexContext.path} ]"
        )
        return parameterJunctionVertexContext.compositeParameterContainerType
            .getParameterContainerTypeByDataSource()
            .values
            .asSequence()
            .filterIsInstance<GraphQLParameterContainerType>()
            .firstOrNull()
            .toOption()
            .zip(
                parameterJunctionVertexContext.compositeParameterAttribute
                    .getParameterAttributesByDataSource()
                    .values
                    .asSequence()
                    .filterIsInstance<GraphQLParameterAttribute>()
                    .firstOrNull()
                    .toOption()
            ) { gqlpct, gqlpa ->
                createParameterSDLDefinitionForGraphQLParameterAttributeAndContainerTypeInJunctionContext(
                    parameterJunctionVertexContext,
                    gqlpct,
                    gqlpa
                )
            }
            .fold(
                {
                    val parameterContainerSourceIndexTypesAvailableSet =
                        parameterJunctionVertexContext.compositeParameterContainerType
                            .getParameterContainerTypeByDataSource()
                            .values
                            .joinToString(
                                ", ",
                                "{ ",
                                " }",
                                transform = { "${it::class.qualifiedName}" }
                            )
                    GQLDataSourceException(
                            GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                            """both composite_parameter_container_type 
                                |and composite_parameter_attribute were 
                                |expected to contain graphql_source_indices 
                                |for this path: ${parameterJunctionVertexContext.path} 
                                |[ actual: $parameterContainerSourceIndexTypesAvailableSet ]
                                |""".flattenIntoOneLine()
                        )
                        .failure()
                },
                ::identity
            )
    }

    fun createParameterSDLDefinitionForParameterLeafVertexInContext(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            "create_parameter_sdl_definition_for_parameter_leaf_vertex_in_context: [ path: ${parameterLeafVertexContext.path} ]"
        )
        return parameterLeafVertexContext.compositeParameterAttribute
            .getParameterAttributesByDataSource()
            .values
            .asSequence()
            .filterIsInstance<GraphQLParameterAttribute>()
            .firstOrNull()
            .toOption()
            .fold(
                {
                    val parameterAttributeSourceIndicesAvailable: String =
                        parameterLeafVertexContext.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .values
                            .joinToString(
                                ", ",
                                "{ ",
                                " }",
                                transform = { "${it::class.qualifiedName}" }
                            )
                    GQLDataSourceException(
                            GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                            """composite_parameter_attribute in parameter_leaf_vertex_context 
                            |expected to contain 
                            |graphql_source_index for this path: 
                            |${parameterLeafVertexContext.path} 
                            |[ expected: ${GraphQLParameterAttribute::class.qualifiedName}, 
                            |actual: ${parameterAttributeSourceIndicesAvailable} 
                            |]""".flattenIntoOneLine()
                        )
                        .failure()
                },
                { graphQLParameterAttribute: GraphQLParameterAttribute ->
                    createParameterSDLDefinitionForGraphQLParameterAttributeInLeafContext(
                        parameterLeafVertexContext,
                        graphQLParameterAttribute
                    )
                }
            )
    }

    fun createParameterSDLDefinitionForGraphQLParameterAttributeAndContainerTypeInJunctionContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        graphQLParameterContainer: GraphQLParameterContainerType,
        graphQLParameterAttribute: GraphQLParameterAttribute
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        return when {
            graphQLParameterAttribute.isArgumentOnFieldDefinition() -> {
                createInputValueAndObjectDefinitionsForFieldArgumentInParameterJunctionVertexContext(
                    parameterJunctionVertexContext,
                    graphQLParameterAttribute,
                    graphQLParameterContainer,
                    graphQLParameterAttribute.fieldArgument.orNull()!!
                )
            }
            graphQLParameterAttribute.isArgumentOnDirective() -> {
                createInputValueAndObjectDefinitionsForDirectiveArgumentInParameterJunctionVertexContext(
                    parameterJunctionVertexContext,
                    graphQLParameterAttribute,
                    graphQLParameterContainer,
                    graphQLParameterAttribute.directiveArgument.orNull()!!
                )
            }
            graphQLParameterAttribute.isFieldOnInputObject() -> {
                createInputValueAndObjectDefinitionsForInputObjectFieldInParameterJunctionVertexContext(
                    parameterJunctionVertexContext,
                    graphQLParameterAttribute,
                    graphQLParameterContainer,
                    graphQLParameterAttribute.inputObjectField.orNull()!!
                )
            }
            graphQLParameterAttribute.isDirective() -> {
                // Ignore for now and let materialization directives be added separately
                parameterJunctionVertexContext.successIfNonNull()
            }
            else -> {
                GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """unhandled_parameter_attribute_case: 
                           |cannot create SDL input_value_definition 
                           |and/or input_object_type_definition for 
                           |vertex in parameter_junction_vertex_context 
                           |[ actual: { parameter_attribute.type: 
                           |${graphQLParameterAttribute::class.qualifiedName} } ]
                           |""".flattenIntoOneLine()
                    )
                    .failure()
            }
        }
    }

    fun createInputValueAndObjectDefinitionsForInputObjectFieldInParameterJunctionVertexContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        graphQLParameterAttribute: GraphQLParameterAttribute,
        graphQLParameterContainer: GraphQLParameterContainerType,
        inputObjectField: GraphQLInputObjectField,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        return createInputValueDefinitionForInputObjectFieldOnParameterAttribute(
                graphQLParameterAttribute,
                inputObjectField
            )
            .map { inputValueDefinition: InputValueDefinition ->
                if (parameterJunctionVertexContext.existingInputObjectTypeDefinition.isDefined()) {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                } else {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            InputObjectTypeDefinition.newInputObjectDefinition()
                                .name(graphQLParameterContainer.name.toString())
                                .build()
                        )
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                }
            }
    }

    fun createInputValueDefinitionForInputObjectFieldOnParameterAttribute(
        graphQLParameterAttribute: GraphQLParameterAttribute,
        inputObjectField: GraphQLInputObjectField,
    ): Try<InputValueDefinition> {
        return if (
            graphQLParameterAttribute.inputObjectField
                .filter { iof -> iof.hasSetDefaultValue() }
                .isDefined()
        ) {
            Try.attempt {
                val literalValue: Value<*> =
                    ValuesResolver.valueToLiteral(
                        inputObjectField.inputFieldDefaultValue,
                        inputObjectField.type
                    )
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(inputObjectField.type))
                    .defaultValue(literalValue)
                    .build()
            }
        } else {
            Try.attempt {
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(inputObjectField.type))
                    .build()
            }
        }
    }

    fun createInputValueAndObjectDefinitionsForDirectiveArgumentInParameterJunctionVertexContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        graphQLParameterAttribute: GraphQLParameterAttribute,
        graphQLParameterContainer: GraphQLParameterContainerType,
        directiveArgument: GraphQLAppliedDirectiveArgument,
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        return createInputValueDefinitionForDirectiveArgumentOnParameterAttribute(
                graphQLParameterAttribute,
                directiveArgument
            )
            .map { inputValueDefinition: InputValueDefinition ->
                if (parameterJunctionVertexContext.existingInputObjectTypeDefinition.isDefined()) {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                } else {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            InputObjectTypeDefinition.newInputObjectDefinition()
                                .name(graphQLParameterContainer.name.toString())
                                .build()
                        )
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                }
            }
    }

    fun createInputValueDefinitionForDirectiveArgumentOnParameterAttribute(
        graphQLParameterAttribute: GraphQLParameterAttribute,
        directiveArgument: GraphQLAppliedDirectiveArgument,
    ): Try<InputValueDefinition> {
        return if (
            graphQLParameterAttribute.directiveArgument
                .filter { dirArg -> dirArg.hasSetValue() }
                .isDefined()
        ) {
            Try.attempt {
                val literalValue: Value<*> =
                    ValuesResolver.valueToLiteral(
                        directiveArgument.argumentValue,
                        directiveArgument.type
                    )
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(directiveArgument.type))
                    .defaultValue(literalValue)
                    .build()
            }
        } else {
            Try.attempt {
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(directiveArgument.type))
                    .build()
            }
        }
    }

    fun createInputValueAndObjectDefinitionsForFieldArgumentInParameterJunctionVertexContext(
        parameterJunctionVertexContext: ParameterJunctionVertexSDLDefinitionCreationContext,
        graphQLParameterAttribute: GraphQLParameterAttribute,
        graphQLParameterContainer: GraphQLParameterContainerType,
        fieldArgument: GraphQLArgument
    ): Try<SchematicVertexSDLDefinitionCreationContext<ParameterJunctionVertex>> {
        return createInputValueDefinitionForFieldArgumentOnParameterAttribute(
                graphQLParameterAttribute,
                fieldArgument
            )
            .map { inputValueDefinition: InputValueDefinition ->
                if (parameterJunctionVertexContext.existingInputObjectTypeDefinition.isDefined()) {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                } else {
                    parameterJunctionVertexContext.update {
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            InputObjectTypeDefinition.newInputObjectDefinition()
                                .name(graphQLParameterContainer.name.toString())
                                .build()
                        )
                        addSDLDefinitionForSchematicPath(
                            parameterJunctionVertexContext.path,
                            inputValueDefinition
                        )
                    }
                }
            }
    }

    fun createInputValueDefinitionForFieldArgumentOnParameterAttribute(
        graphQLParameterAttribute: GraphQLParameterAttribute,
        fieldArgument: GraphQLArgument,
    ): Try<InputValueDefinition> {
        return if (
            graphQLParameterAttribute.fieldArgument
                .filter { arg -> arg.hasSetDefaultValue() }
                .isDefined()
        ) {
            Try.attempt {
                val literalValue: Value<*> =
                    ValuesResolver.valueToLiteral(
                        fieldArgument.argumentDefaultValue,
                        fieldArgument.type
                    )
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(fieldArgument.type))
                    .defaultValue(literalValue)
                    .build()
            }
        } else {
            Try.attempt {
                InputValueDefinition.newInputValueDefinition()
                    .name(graphQLParameterAttribute.name.toString())
                    .type(GraphQLSDLTypeComposer.invoke(fieldArgument.type))
                    .build()
            }
        }
    }

    fun createParameterSDLDefinitionForGraphQLParameterAttributeInLeafContext(
        parameterLeafVertexContext: ParameterLeafVertexSDLDefinitionCreationContext,
        graphQLParameterAttribute: GraphQLParameterAttribute
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        if (graphQLParameterAttribute.isDirective()) {
            // Ignore for now, let materialization directives be configured elsewhere
            return parameterLeafVertexContext.successIfNonNull()
        }
        return when {
            graphQLParameterAttribute.isArgumentOnFieldDefinition() -> {
                createInputValueDefinitionForFieldArgumentOnParameterAttribute(
                    graphQLParameterAttribute,
                    graphQLParameterAttribute.fieldArgument.orNull()!!
                )
            }
            graphQLParameterAttribute.isArgumentOnDirective() -> {
                createInputValueDefinitionForDirectiveArgumentOnParameterAttribute(
                    graphQLParameterAttribute,
                    graphQLParameterAttribute.directiveArgument.orNull()!!
                )
            }
            graphQLParameterAttribute.isFieldOnInputObject() -> {
                createInputValueDefinitionForInputObjectFieldOnParameterAttribute(
                    graphQLParameterAttribute,
                    graphQLParameterAttribute.inputObjectField.orNull()!!
                )
            }
            else -> {
                GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """unhandled_parameter_attribute_case: 
                           |cannot create SDL input_value_definition 
                           |for vertex in parameter_junction_vertex_context 
                           |[ actual: { parameter_attribute.type: 
                           |${graphQLParameterAttribute::class.qualifiedName} } ]
                           |""".flattenIntoOneLine()
                    )
                    .failure()
            }
        }.map { inputValueDefinition: InputValueDefinition ->
            parameterLeafVertexContext.update {
                addSDLDefinitionForSchematicPath(
                    parameterLeafVertexContext.path,
                    inputValueDefinition
                )
            }
        }
    }
}
