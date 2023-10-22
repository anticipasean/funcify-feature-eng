package funcify.feature.schema.path.result

import arrow.core.Option
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentListExtensions.toPersistentList
import graphql.execution.ResultPath
import java.net.URI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

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

        @JvmStatic
        fun parse(pathAsString: String): Try<GQLResultPath> {
            return GQLResultPathParser.invoke(pathAsString)
        }

        /** @throws IllegalArgumentException if not in correct format */
        @JvmStatic
        fun parseOrThrow(pathAsString: String): GQLResultPath {
            return GQLResultPathParser.invoke(pathAsString).orElseThrow()
        }

        @JvmStatic
        fun parseOrNull(pathAsString: String): GQLResultPath? {
            return GQLResultPathParser.invoke(pathAsString).orNull()
        }

        @JvmStatic
        fun fromNativeResultPath(nativePath: ResultPath): GQLResultPath {
            return NativeToSchemaResultPathTransformer.invoke(nativePath)
        }

        @JvmStatic
        fun fromOperationPath(operationPath: GQLOperationPath): Try<GQLResultPath> {
            return OperationToSchemaResultPathTransformer.invoke(operationPath)
        }

        @JvmStatic
        fun fromOperationPathOrThrow(operationPath: GQLOperationPath): GQLResultPath {
            return OperationToSchemaResultPathTransformer.invoke(operationPath).orElseThrow()
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

        fun appendNameSegment(name: String): Builder {
            return if (name.isNotBlank()) {
                appendElementSegment(NameSegment(name = name.trim()))
            } else {
                this
            }
        }

        fun prependNamedSegment(name: String): Builder {
            return if (name.isNotBlank()) {
                prependElementSegment(NameSegment(name = name.trim()))
            } else {
                this
            }
        }

        fun nameSegment(name: String): Builder {
            return appendNameSegment(name)
        }

        fun appendListSegment(name: String, index: Int): Builder {
            return if (index >= 0) {
                appendElementSegment(
                    ListSegment(name = name.trim(), indices = persistentListOf(index))
                )
            } else {
                this
            }
        }

        fun prependListSegment(name: String, index: Int): Builder {
            return if (index >= 0) {
                prependElementSegment(
                    ListSegment(name = name.trim(), indices = persistentListOf(index))
                )
            } else {
                this
            }
        }

        fun listSegment(name: String, index: Int): Builder {
            return appendListSegment(name, index)
        }

        fun appendNestedListSegment(name: String, vararg index: Int): Builder {
            return if (index.isNotEmpty() && index.all { i: Int -> i >= 0 }) {
                appendElementSegment(
                    ListSegment(name = name.trim(), indices = index.toPersistentList())
                )
            } else {
                this
            }
        }

        fun prependNestedListSegment(name: String, vararg index: Int): Builder {
            return if (index.isNotEmpty() && index.all { i: Int -> i >= 0 }) {
                prependElementSegment(
                    ListSegment(name = name.trim(), indices = index.toPersistentList())
                )
            } else {
                this
            }
        }

        fun nestedListSegment(name: String, vararg index: Int): Builder {
            return appendNestedListSegment(name, *index)
        }

        fun appendNestedListSegment(name: String, indices: List<Int>): Builder {
            return if (indices.isNotEmpty() && indices.all { i: Int -> i >= 0 }) {
                appendElementSegment(
                    ListSegment(name = name.trim(), indices = indices.toPersistentList())
                )
            } else {
                this
            }
        }

        fun prependNestedListSegment(name: String, indices: List<Int>): Builder {
            return if (indices.isNotEmpty() && indices.all { i: Int -> i >= 0 }) {
                prependElementSegment(
                    ListSegment(name = name.trim(), indices = indices.toPersistentList())
                )
            } else {
                this
            }
        }

        fun nestedListSegment(name: String, indices: List<Int>): Builder {
            return appendNestedListSegment(name, indices)
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
