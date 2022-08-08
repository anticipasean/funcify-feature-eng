package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.none
import arrow.core.or
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputFieldsContainer
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
interface GraphQLParameterContainerType :
    GraphQLParameterIndex, ParameterContainerType<GraphQLSourceIndex, GraphQLParameterAttribute> {

    override val dataType: GraphQLInputType
        get() =
            fieldArgument
                .map(GraphQLArgument::getType)
                .or(directiveArgument.map(GraphQLAppliedDirectiveArgument::getType))
                .or(inputObjectField.map(GraphQLInputObjectField::getType))
                .or(
                    /**
                     * pseudo-graphqlinputobjecttype for directive since it acts as a container_type
                     * but does not itself have a graphql_input_fields_container type like the other
                     * schema_element types e.g. field_arguments, directive_arguments,
                     * input_object_fields, etc. can
                     */
                    directive.map { _ -> name.toString() }.map { s ->
                        GraphQLInputObjectType.newInputObject().name(s).build()
                    }
                )
                .successIfDefined {
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.INVALID_INPUT,
                        """a graphql_field_argument, graphql_input_object_field, 
                            |graphql_applied_directive, OR 
                            |graphql_applied_directive_argument must be defined 
                            |for a parameter_container_type 
                            |(or this property must be overridden and supplied some
                            |other way)""".flatten()
                    )
                }
                .orElseThrow()

    val inputFieldsContainerType: GraphQLInputFieldsContainer

    val fieldArgument: Option<GraphQLArgument>
        get() = none()

    val directive: Option<GraphQLAppliedDirective>
        get() = none()

    val inputObjectField: Option<GraphQLInputObjectField>
        get() = none()

    val directiveArgument: Option<GraphQLAppliedDirectiveArgument>
        get() = none()

    fun isDirectiveContainerType(): Boolean {
        return directive.isDefined()
    }

    fun isArgumentContainerTypeOnFieldDefinition(): Boolean {
        return fieldArgument.isDefined()
    }

    fun isFieldContainerTypeOnInputObject(): Boolean {
        return inputObjectField.isDefined()
    }

    fun isArgumentContainerTypeOnDirective(): Boolean {
        return directiveArgument.isDefined()
    }
}
