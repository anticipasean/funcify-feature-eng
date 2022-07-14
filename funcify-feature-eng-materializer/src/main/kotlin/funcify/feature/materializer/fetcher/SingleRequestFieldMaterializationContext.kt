package funcify.feature.materializer.fetcher

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

    val currentGraphQLContext: GraphQLContext
        get() = dataFetchingEnvironment.graphQlContext

    val currentGraphQLFieldDefinition: GraphQLFieldDefinition
        get() = dataFetchingEnvironment.fieldDefinition

    val currentField: Field
        get() = dataFetchingEnvironment.field

    val currentFieldOutputType: GraphQLOutputType
        get() = dataFetchingEnvironment.fieldType
}
