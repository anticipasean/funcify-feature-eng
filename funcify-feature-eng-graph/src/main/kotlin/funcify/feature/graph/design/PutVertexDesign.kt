package funcify.feature.graph.design

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.logging.Logger
import kotlin.math.log

internal class PutVertexDesign<SWT, P, V, E>(
    override val template: PersistentGraphTemplate<SWT>,
    val currentDesign: PersistentGraphDesign<SWT, P, V, E>,
    val path: P,
    val newVertex: V
) : PersistentGraphDesign<SWT, P, V, E>(template) {

    companion object {
        private val logger: Logger = Logger.getLogger(PutVertexDesign::class.simpleName)
    }

    override fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E> {
        logger.info("fold: { path: $path, vertex: $newVertex }")
        return template.put(path, newVertex, currentDesign.fold(template))
    }
}
