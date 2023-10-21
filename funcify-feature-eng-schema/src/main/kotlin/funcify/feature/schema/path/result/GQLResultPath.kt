package funcify.feature.schema.path.result

import arrow.core.Option
import arrow.core.identity
import arrow.core.lastOrNone
import java.net.URI
import kotlinx.collections.immutable.ImmutableList

/**
 * @author smccarron
 * @created 2023-10-19
 */
interface GQLResultPath : Comparable<GQLResultPath> {

    companion object {

        const val GQL_RESULT_PATH_SCHEME: String = "gqlr"

        private val rootPath: GQLResultPath = DefaultGQLResultPath()

        @JvmStatic
        fun getRootPath(): GQLResultPath {
            return rootPath
        }

        @JvmStatic
        fun of(builderFunction: Builder.() -> Builder): GQLResultPath {
            return rootPath.transform(builderFunction)
        }

        @JvmStatic
        fun comparator(): Comparator<GQLResultPath> {
            return GQLResultPathComparator
        }

        /** @throws IllegalArgumentException if not in correct format */
        @JvmStatic
        fun parseOrThrow(pathAsString: String): GQLResultPath {
            return GQLResultPathParser.invoke(pathAsString)
                .fold({ iae: IllegalArgumentException -> throw iae }, ::identity)
        }

        @JvmStatic
        fun parseOrNull(pathAsString: String): GQLResultPath? {
            return GQLResultPathParser.invoke(pathAsString)
                .fold({ _: IllegalArgumentException -> null }, ::identity)
        }
    }

    val scheme: String

    val elementSegments: ImmutableList<ElementSegment>

    fun toURI(): URI

    fun transform(mapper: Builder.() -> Builder): GQLResultPath

    fun getParentPath(): Option<GQLResultPath>

    override fun compareTo(other: GQLResultPath): Int {
        return comparator().compare(this, other)
    }

    interface Builder {

        fun scheme(scheme: String): Builder

        fun appendNamedSegment(name: String): Builder {
            return if (name.isNotBlank()) {
                appendElementSegment(NamedSegment(name = name.trim()))
            } else {
                this
            }
        }

        fun prependNamedSegment(name: String): Builder {
            return if (name.isNotBlank()) {
                prependElementSegment(NamedSegment(name = name.trim()))
            } else {
                this
            }
        }

        fun namedSegment(name: String): Builder {
            return appendNamedSegment(name)
        }

        fun appendNamedListSegment(name: String, index: Int): Builder {
            return if (name.isNotBlank() && index >= 0) {
                appendElementSegment(NamedListSegment(name = name.trim(), index = index))
            } else {
                this
            }
        }

        fun prependNamedListSegment(name: String, index: Int): Builder {
            return if (name.isNotBlank() && index >= 0) {
                prependElementSegment(NamedListSegment(name = name.trim(), index = index))
            } else {
                this
            }
        }

        fun namedListSegment(name: String, index: Int): Builder {
            return appendNamedListSegment(name, index)
        }

        fun appendUnnamedListSegment(index: Int): Builder {
            return if (index >= 0) {
                appendElementSegment(UnnamedListSegment(index = index))
            } else {
                this
            }
        }

        fun prependUnnamedListSegment(index: Int): Builder {
            return if (index >= 0) {
                prependElementSegment(UnnamedListSegment(index = index))
            } else {
                this
            }
        }

        fun unnamedListSegment(index: Int): Builder {
            return appendUnnamedListSegment(index)
        }

        fun prependElementSegment(vararg elementSegment: ElementSegment): Builder

        fun prependElementSegments(elementSegments: List<ElementSegment>): Builder

        fun appendElementSegment(vararg elementSegment: ElementSegment): Builder

        fun appendElementSegments(elementSegments: List<ElementSegment>): Builder

        fun dropHeadElementSegment(): Builder

        fun dropTailElementSegment(): Builder

        fun clearElementSegments(): Builder

        fun build(): GQLResultPath
    }
}
