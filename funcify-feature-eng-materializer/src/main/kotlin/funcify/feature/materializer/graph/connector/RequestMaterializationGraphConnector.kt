package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.GraphQLQueryGraphConnector
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext

/**
 * @author smccarron
 * @created 2023-08-06
 */
interface RequestMaterializationGraphConnector<C : RequestMaterializationGraphContext> :
    GraphQLQueryGraphConnector<C> {



    }
