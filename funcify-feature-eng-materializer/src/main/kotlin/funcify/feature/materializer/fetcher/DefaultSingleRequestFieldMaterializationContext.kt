package funcify.feature.materializer.fetcher

import graphql.schema.DataFetchingEnvironment

data class DefaultSingleRequestFieldMaterializationContext(
    override val dataFetchingEnvironment: DataFetchingEnvironment
) : SingleRequestFieldMaterializationContext {}
