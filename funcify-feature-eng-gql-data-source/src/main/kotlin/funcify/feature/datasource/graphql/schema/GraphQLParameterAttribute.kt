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

    val argument: Option<GraphQLArgument>
        get() = none()

    val directive: Option<GraphQLAppliedDirective>
        get() = none()

    val inputObjectField: Option<GraphQLInputObjectField>
        get() = none()

    val appliedDirectiveArgument: Option<GraphQLAppliedDirectiveArgument>
        get() = none()

    fun isOnDirective(): Boolean {
        return directive.isDefined()
    }

    fun isOnArgument(): Boolean {
        return argument.isDefined()
    }

    fun isOnInputObject(): Boolean {
        return inputObjectField.isDefined()
    }

    fun isArgumentOnDirective(): Boolean {
        return appliedDirectiveArgument.isDefined()
    }
}
