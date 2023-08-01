package funcify.feature.materializer.graph

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import graphql.execution.ExecutionContext

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface MaterializationGraphContext {

    val session: GraphQLSingleRequestSession

    val executionContext: ExecutionContext
}
