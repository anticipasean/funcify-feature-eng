package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.ParameterAttribute
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectField

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
interface GraphQLParameterAttribute :
    GraphQLParameterIndex, ParameterAttribute<GraphQLSourceIndex> {

    val fieldArgument: Option<GraphQLArgument>
        get() = none()

    val directive: Option<GraphQLAppliedDirective>
        get() = none()

    val inputObjectField: Option<GraphQLInputObjectField>
        get() = none()

    val directiveArgument: Option<GraphQLAppliedDirectiveArgument>
        get() = none()

    fun isDirective(): Boolean {
        return directive.isDefined()
    }

    fun isArgumentOnFieldDefinition(): Boolean {
        return fieldArgument.isDefined()
    }

    fun isFieldOnInputObject(): Boolean {
        return inputObjectField.isDefined()
    }

    fun isArgumentOnDirective(): Boolean {
        return directiveArgument.isDefined()
    }
}
