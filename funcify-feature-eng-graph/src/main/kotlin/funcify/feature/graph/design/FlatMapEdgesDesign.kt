package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class FlatMapEdgesDesign<SWT, P, V, E, R, M : Map<out Pair<P, P>, R>>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val mapper: (Pair<P, P>, E) -> M
) : PersistentGraphDesign<SWT, P, V, R>(template) {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, R> {
        return template.flatMapEdges(mapper, currentDesign.fold(template))
    }
}
