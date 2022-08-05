package funcify.feature.materializer.fetcher

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.GraphQLContext
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface SingleRequestFieldMaterializationContext {

    val dataFetchingEnvironment: DataFetchingEnvironment

    val singleRequestSession: GraphQLSingleRequestSession

    val currentGraphQLContext: GraphQLContext
        get() = dataFetchingEnvironment.graphQlContext

    val currentGraphQLFieldDefinition: GraphQLFieldDefinition
        get() = dataFetchingEnvironment.fieldDefinition

    val currentField: Field
        get() = dataFetchingEnvironment.field

    val currentFieldOutputType: GraphQLOutputType
        get() = dataFetchingEnvironment.fieldType
}
