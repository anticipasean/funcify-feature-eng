package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class PutEdgeSetsDesign<SWT, P, V, E>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val edgeSets: Map<Pair<P, P>, Set<E>>
) : PersistentGraphDesign<SWT, P, V, E>(template) {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E> {
        return template.putAllEdgeSets(edgeSets, currentDesign.fold(template))
    }
}
