package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.ParameterContainerType
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLInputFieldsContainer

/**
 *
 * @author smccarron
 * @created 2022-07-03
 */
interface GraphQLParameterContainerType :
    GraphQLParameterIndex, ParameterContainerType<GraphQLSourceIndex, GraphQLParameterAttribute> {

    val graphQLInputFieldsContainerType: Option<GraphQLInputFieldsContainer>
        get() = none()

    val graphQLAppliedDirective: Option<GraphQLAppliedDirective>
        get() = none()

    fun isDirective(): Boolean {
        return graphQLAppliedDirective.isDefined()
    }

    fun isInputObject(): Boolean {
        return graphQLInputFieldsContainerType.isDefined()
    }
}
