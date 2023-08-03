package funcify.feature.materializer.graph

import funcify.feature.materializer.session.GraphQLSingleRequestSession

internal data class DefaultRequestMaterializationGraphContext(
    override val session: GraphQLSingleRequestSession
                                                             ) : RequestMaterializationGraphContext
