package funcify.feature.schema.path

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface SchematicPathFactory {

    companion object {

        fun createRootPath(): SchematicPath {
            return DefaultSchematicPath()
        }

        fun createPathWithSegments(pathSegments: Iterable<String>): SchematicPath {
            return when (pathSegments) {
                is PersistentList -> {
                    if (pathSegments.none { s -> s.isEmpty() }) {
                        DefaultSchematicPath(pathSegments = pathSegments)
                    } else {
                        DefaultSchematicPath(pathSegments = pathSegments.filter { s -> s.isNotEmpty() }
                                .toPersistentList())
                    }
                }
                else -> {
                    DefaultSchematicPath(pathSegments = pathSegments.filter { s -> s.isNotEmpty() }
                            .toPersistentList())
                }
            }
        }

        fun createPathWithSegments(vararg pathSegments: String): SchematicPath {
            return createPathWithSegments(pathSegments.asIterable())
        }

    }

}