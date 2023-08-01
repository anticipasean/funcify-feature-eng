package funcify.feature.materializer.graph

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.execution.ExecutionContext
import graphql.normalized.ExecutableNormalizedField
import graphql.normalized.ExecutableNormalizedOperation

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface MaterializationGraphContext {

    val session: GraphQLSingleRequestSession

    val executionContext: ExecutionContext

}
