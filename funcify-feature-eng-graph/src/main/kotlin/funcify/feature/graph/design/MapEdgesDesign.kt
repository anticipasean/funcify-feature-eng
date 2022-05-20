package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class MapEdgesDesign<SWT, P, V, E, R>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val mapper: (E) -> R
) : PersistentGraphDesign<SWT, P, V, R> {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, R> {
        return template.mapEdges(mapper, currentDesign.fold(template))
    }
}
