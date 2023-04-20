package funcify.feature.tree.path

import arrow.core.Option
import arrow.core.lastOrNone
import java.net.URI
import kotlinx.collections.immutable.ImmutableList

/**
 *
 * @author smccarron
 * @created 2023-04-08
 */
interface TreePath : Comparable<TreePath> {

    companion object {

        const val GRAPHQL_SCHEMATIC_PATH_SCHEME: String = "tp"

        private val rootPath: TreePath = DefaultTreePath()

        @JvmStatic
        fun getRootPath(): TreePath {
            return rootPath
        }

        @JvmStatic
        fun of(builderFunction: Builder.() -> Builder): TreePath {
            return rootPath.transform(builderFunction)
        }

        @JvmStatic
        fun comparator(): Comparator<TreePath> {
            return TreePathComparator
        }
    }

    val scheme: String

    val pathSegments: ImmutableList<PathSegment>

    /** URI representation of path on which feature function is located within service context */
    fun toURI(): URI

    fun transform(mapper: Builder.() -> Builder): TreePath

    fun lastSegment(): Option<PathSegment> {
        return pathSegments.lastOrNone()
    }

    override fun compareTo(other: TreePath): Int {
        return comparator().compare(this, other)
    }

    interface Builder {

        fun scheme(scheme: String): Builder

        fun prependPathSegment(vararg pathSegment: String): Builder

        fun prependPathSegment(vararg pathSegment: Int): Builder

        fun prependPathSegments(pathSegments: List<String>): Builder

        fun appendPathSegment(vararg pathSegment: String): Builder {
            return pathSegment(*pathSegment)
        }

        fun appendPathSegment(vararg pathSegment: Int): Builder {
            return pathSegment(*pathSegment)
        }

        fun appendPathSegments(pathSegments: List<String>): Builder {
            return pathSegments(pathSegments)
        }

        fun dropPathSegment(): Builder

        fun pathSegment(vararg pathSegment: String): Builder

        fun pathSegment(vararg pathSegment: Int): Builder

        fun pathSegments(pathSegments: List<String>): Builder

        fun clearPathSegments(): Builder

        fun build(): TreePath
    }
}
