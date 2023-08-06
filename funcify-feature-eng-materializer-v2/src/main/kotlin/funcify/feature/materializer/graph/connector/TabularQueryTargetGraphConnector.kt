package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.target.TabularQueryTarget
import graphql.language.OperationDefinition

/**
 * @author smccarron
 * @created 2023-08-05
 */
interface TabularQueryTargetGraphConnector<C> : RequestMaterializationGraphConnector<C> where
C : RequestMaterializationGraphContext,
C : TabularQueryTarget {

    fun createOperationDefinition(context: C): OperationDefinition {
        TODO()
    }
}
