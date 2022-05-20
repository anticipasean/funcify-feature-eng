package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class MapVerticesDesign<SWT, P, V, E, R>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val mapper: (V) -> R
) : PersistentGraphDesign<SWT, P, R, E> {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, R, E> {
        return template.mapVertices(mapper, currentDesign.fold(template))
    }
}
