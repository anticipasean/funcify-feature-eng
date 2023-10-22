package funcify.feature.schema.path.result

import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

/**
 * @author smccarron
 * @created 2023-10-19
 */
internal object GQLResultPathParser : (String) -> Try<GQLResultPath> {

    private val cache: ConcurrentMap<String, Try<GQLResultPath>> = ConcurrentHashMap()

    override fun invoke(pathAsString: String): Try<GQLResultPath> {
        return cache.computeIfAbsent(pathAsString) { s: String -> parseResultPathFromString(s) }
    }

    private fun parseResultPathFromString(pathAsString: String): Try<GQLResultPath> {
        return try {
            val uri: URI = URI.create(pathAsString)
            if (GQLResultPath.GQL_RESULT_PATH_SCHEME != uri.scheme) {
                Try.failure {
                    IllegalArgumentException(
                        "scheme of uri does not match expected scheme [ expected: %s, actual: %s ]"
                            .format(GQLResultPath.GQL_RESULT_PATH_SCHEME, uri.scheme)
                    )
                }
            } else {
                Try.success(GQLResultPath.of(appendPathSegmentsToBuilder(uri.path)))
            }
        } catch (t: Throwable) {
            Try.failure {
                IllegalArgumentException(
                    "unable to create %s from string: [ type: %s, message: %s ]"
                        .format(GQLResultPath::class.simpleName, t::class.simpleName, t.message)
                )
            }
        }
    }

    private fun appendPathSegmentsToBuilder(
        path: String,
    ): (GQLResultPath.Builder) -> GQLResultPath.Builder {
        return { builder: GQLResultPath.Builder ->
            path
                .splitToSequence('/')
                .filter(String::isNotBlank)
                .map(String::trim)
                .map(convertStringToElementSegment())
                .fold(builder) { bldr: GQLResultPath.Builder, es: ElementSegment ->
                    bldr.appendElementSegment(es)
                }
        }
    }

    private fun convertStringToElementSegment(): (String) -> ElementSegment {
        return { segmentString: String ->
            val firstBracketIndex: Int = segmentString.indexOf('[')
            when {
                firstBracketIndex >= 0 -> {
                    segmentString
                        .substring(firstBracketIndex)
                        .splitToSequence('[')
                        .drop(1)
                        .map { s: String ->
                            val endBracketIndex: Int = s.indexOf(']')
                            when {
                                endBracketIndex < 0 -> {
                                    throw IllegalArgumentException(
                                        """segment contains unterminated 
                                        |list index declaration (=> no end bracket i.e. ']') 
                                        |[ actual: '%s' ]"""
                                            .format(s)
                                            .flatten()
                                    )
                                }
                                endBracketIndex < s.length - 1 -> {
                                    throw IllegalArgumentException(
                                        """segment contains characters after end bracket 
                                            |of index value declaration [ actual: '%s' ]"""
                                            .format(s)
                                            .flatten()
                                    )
                                }
                                else -> {
                                    s.substring(startIndex = 0, endIndex = endBracketIndex)
                                }
                            }
                        }
                        .map { s: String ->
                            s.toIntOrNull()
                                ?: throw IllegalArgumentException(
                                    "value between brackets not parseable as %s [ actual: '%s' ]"
                                        .format(Int::class.simpleName, s)
                                )
                        }
                        .toPersistentList()
                        .let { indices: PersistentList<Int> ->
                            ListSegment(
                                name = segmentString.substring(0, firstBracketIndex).trimEnd(),
                                indices = indices
                            )
                        }
                }
                else -> {
                    NameSegment(name = segmentString)
                }
            }
        }
    }
}
