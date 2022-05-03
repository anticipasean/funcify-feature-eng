package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate

internal class FilterEdgesDesign<SWT, P, V, E>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val condition: (E) -> Boolean
) : PersistentGraphDesign<SWT, P, V, E> {

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E> {
        return template.filterEdges(condition, currentDesign.fold(template))
    }
}
