package funcify.feature.materializer.graph

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.schema.path.GQLOperationPath
import graphql.language.Document
import graphql.language.Node
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface RequestMaterializationGraphContext {

    val variableKeys: ImmutableSet<String>

    val requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>

    interface RawInputBasedStandardQuery : RequestMaterializationGraphContext {

        val document: Document

        val rawInputContextShape: RawInputContextShape
    }

    interface StandardQuery : RequestMaterializationGraphContext {

        val document: Document
    }

    interface RawInputBasedTabularQuery : RequestMaterializationGraphContext {

        val rawInputContextShape: RawInputContextShape

        val outputColumnNames: ImmutableSet<String>
    }

    interface TabularQuery : RequestMaterializationGraphContext {

        val outputColumnNames: ImmutableSet<String>
    }
}
