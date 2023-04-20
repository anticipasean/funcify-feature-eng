package funcify.feature.tree.path

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 *
 * @author smccarron
 * @created 2023-04-09
 */
internal data class DefaultTreePath(
    override val scheme: String = TreePath.GRAPHQL_SCHEMATIC_PATH_SCHEME,
    override val pathSegments: PersistentList<PathSegment> = persistentListOf()
) : TreePath {

    companion object {

        internal class DefaultBuilder(
            private var scheme: String = TreePath.GRAPHQL_SCHEMATIC_PATH_SCHEME,
            private val pathSegmentsBuilder: PersistentList.Builder<PathSegment> =
                persistentListOf<PathSegment>().builder()
        ) : TreePath.Builder {

            override fun scheme(scheme: String): TreePath.Builder {
                this.scheme = scheme
                return this
            }

            override fun prependPathSegment(vararg pathSegment: String): TreePath.Builder {
                pathSegment
                    .asSequence()
                    .filter { ps: String -> ps.isNotBlank() }
                    .forEach { ps: String -> pathSegmentsBuilder.add(0, NameSegment(ps)) }
                return this
            }

            override fun prependPathSegment(vararg pathSegment: Int): TreePath.Builder {
                pathSegment
                    .asSequence()
                    .filter { ps: Int -> ps >= 0 }
                    .forEach { ps: Int -> pathSegmentsBuilder.add(0, IndexSegment(ps)) }
                return this
            }

            override fun prependPathSegments(pathSegments: List<String>): TreePath.Builder {
                pathSegments
                    .asSequence()
                    .filter { ps: String -> ps.isNotBlank() }
                    .forEach { ps: String -> pathSegmentsBuilder.add(0, NameSegment(ps)) }
                return this
            }

            override fun dropPathSegment(): TreePath.Builder {
                if (pathSegmentsBuilder.isNotEmpty()) {
                    pathSegmentsBuilder.remove(pathSegmentsBuilder[pathSegmentsBuilder.size - 1])
                }
                return this
            }

            override fun pathSegment(vararg pathSegment: String): TreePath.Builder {
                pathSegment
                    .asSequence()
                    .filter { ps: String -> ps.isNotBlank() }
                    .forEach { ps: String -> pathSegmentsBuilder.add(NameSegment(ps)) }
                return this
            }

            override fun pathSegment(vararg pathSegment: Int): TreePath.Builder {
                pathSegment
                    .asSequence()
                    .filter { ps: Int -> ps >= 0 }
                    .forEach { ps: Int -> pathSegmentsBuilder.add(IndexSegment(ps)) }
                return this
            }

            override fun pathSegments(pathSegments: List<String>): TreePath.Builder {
                pathSegments
                    .asSequence()
                    .filter { ps: String -> ps.isNotBlank() }
                    .forEach { ps: String -> pathSegmentsBuilder.add(NameSegment(ps)) }
                return this
            }

            override fun clearPathSegments(): TreePath.Builder {
                pathSegmentsBuilder.clear()
                return this
            }

            override fun build(): TreePath {
                return DefaultTreePath(scheme = scheme, pathSegments = pathSegmentsBuilder.build())
            }
        }
    }

    private val uri: URI by lazy {
        URI.create(
            buildString {
                append(scheme)
                append(":")
                append(
                    pathSegments.joinToString("/", "/") { ps: PathSegment ->
                        ps.fold(
                            { i: Int -> i.toString() },
                            { n: String -> URLEncoder.encode(n, StandardCharsets.UTF_8) }
                        )
                    }
                )
            }
        )
    }

    override fun toURI(): URI {
        return uri
    }

    override fun transform(mapper: TreePath.Builder.() -> TreePath.Builder): TreePath {
        return mapper
            .invoke(
                DefaultBuilder(
                    scheme = this.scheme,
                    pathSegmentsBuilder = this.pathSegments.builder()
                )
            )
            .build()
    }
}
