package funcify.feature.materializer.fetcher

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.schema.DataFetchingEnvironment

data class DefaultSingleRequestFieldMaterializationContext(
    override val dataFetchingEnvironment: DataFetchingEnvironment,
    override val singleRequestSession: GraphQLSingleRequestSession
) : SingleRequestFieldMaterializationContext {}
