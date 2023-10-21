package funcify.feature.schema.path.result

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * @author smccarron
 * @created 2023-10-19
 */
internal data class DefaultGQLResultPath(
    override val scheme: String = GQLResultPath.GQL_RESULT_PATH_SCHEME,
    override val elementSegments: PersistentList<ElementSegment> = persistentListOf()
) : GQLResultPath {

    companion object {

        internal class DefaultBuilder(
            private val existingPath: DefaultGQLResultPath? = null,
            private var scheme: String =
                existingPath?.scheme ?: GQLResultPath.GQL_RESULT_PATH_SCHEME,
            private val elementSegmentsBuilders: PersistentList.Builder<ElementSegment> =
                existingPath?.elementSegments?.builder()
                    ?: persistentListOf<ElementSegment>().builder()
        ) : GQLResultPath.Builder {

            override fun scheme(scheme: String): GQLResultPath.Builder =
                this.apply {
                    this.scheme =
                        scheme.toOption().map(String::trim).filter(String::isNotEmpty).getOrElse {
                            this.scheme
                        }
                }

            override fun prependElementSegment(
                vararg elementSegment: ElementSegment
            ): GQLResultPath.Builder =
                this.apply {
                    elementSegment.reversed().forEach { es: ElementSegment ->
                        elementSegmentsBuilders.add(0, es)
                    }
                }

            override fun prependElementSegments(
                elementSegments: List<ElementSegment>
            ): GQLResultPath.Builder =
                this.apply {
                    elementSegments.reversed().forEach { es: ElementSegment ->
                        elementSegmentsBuilders.add(0, es)
                    }
                }

            override fun appendElementSegment(
                vararg elementSegment: ElementSegment
            ): GQLResultPath.Builder =
                this.apply {
                    elementSegment.forEach { es: ElementSegment -> elementSegmentsBuilders.add(es) }
                }

            override fun appendElementSegments(
                elementSegments: List<ElementSegment>
            ): GQLResultPath.Builder =
                this.apply {
                    elementSegments.forEach { es: ElementSegment ->
                        elementSegmentsBuilders.add(es)
                    }
                }

            override fun dropHeadElementSegment(): GQLResultPath.Builder =
                this.apply {
                    if (elementSegmentsBuilders.isNotEmpty()) {
                        elementSegmentsBuilders.removeFirst()
                    }
                }

            override fun dropTailElementSegment(): GQLResultPath.Builder =
                this.apply {
                    if (elementSegmentsBuilders.isNotEmpty()) {
                        elementSegmentsBuilders.removeLast()
                    }
                }

            override fun clearElementSegments(): GQLResultPath.Builder =
                this.apply { elementSegmentsBuilders.clear() }

            override fun build(): GQLResultPath {
                return DefaultGQLResultPath(
                    scheme = scheme,
                    elementSegments = elementSegmentsBuilders.build()
                )
            }
        }
    }

    private val internedUri: URI by lazy {
        URI.create(
            buildString {
                append(scheme)
                append(":")
                append(
                    elementSegments
                        .asSequence()
                        .map(ElementSegment::toString)
                        .map { s: String -> URLEncoder.encode(s, StandardCharsets.UTF_8) }
                        .joinToString("/", "/")
                )
            }
        )
    }

    private val internedParent: Option<GQLResultPath> by lazy {
        if (elementSegments.isNotEmpty()) {
            transform { dropTailElementSegment() }.some()
        } else {
            none()
        }
    }

    private val internedStringRep: String by lazy { internedUri.toASCIIString() }

    override fun toURI(): URI {
        return internedUri
    }

    override fun transform(
        mapper: GQLResultPath.Builder.() -> GQLResultPath.Builder
    ): GQLResultPath {
        return mapper.invoke(DefaultBuilder(this)).build()
    }

    override fun getParentPath(): Option<GQLResultPath> {
        return internedParent
    }

    override fun toString(): String {
        return internedStringRep
    }
}
