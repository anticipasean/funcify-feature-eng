package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class PutEdgesDesign<SWT, P, V, E>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val edges: Map<Pair<P, P>, E>
) : PersistentGraphDesign<SWT, P, V, E> {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E> {
        return template.putAllEdges(edges, currentDesign.fold(template))
    }
}
