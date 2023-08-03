package funcify.feature.materializer.graph

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.session.GraphQLSingleRequestSession

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface RequestMaterializationGraphContext {

    val session: GraphQLSingleRequestSession

    val request: DirectedPersistentGraph<>


}
