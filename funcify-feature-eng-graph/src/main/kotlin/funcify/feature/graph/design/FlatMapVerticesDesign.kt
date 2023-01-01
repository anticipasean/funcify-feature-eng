package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class FlatMapVerticesDesign<SWT, P, V, E, R, M : Map<out P, R>>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val mapper: (P, V) -> M
) : PersistentGraphDesign<SWT, P, R, E>(template) {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, R, E> {
        return template.flatMapVertices(mapper, currentDesign.fold(template))
    }
}
