package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class PutEdgeDesign<SWT, P, V, E>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val vertexPath1: P,
    val vertexPath2: P,
    val newEdge: E
) : PersistentGraphDesign<SWT, P, V, E>(template) {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E> {
        return template.put(vertexPath1, vertexPath2, newEdge, currentDesign.fold(template))
    }
}
