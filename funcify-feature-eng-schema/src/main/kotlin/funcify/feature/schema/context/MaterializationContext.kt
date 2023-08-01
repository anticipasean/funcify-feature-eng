package funcify.feature.schema.context

import graphql.schema.DataFetchingEnvironment

/**
 * @author smccarron
 * @created 2023-07-21
 */
interface MaterializationContext {

    val dataFetchingEnvironment: DataFetchingEnvironment
}
