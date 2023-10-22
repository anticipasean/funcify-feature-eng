package funcify.feature.schema.path.result

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.net.URI

/**
 * @author smccarron
 * @created 2023-10-19
 */
internal object GQLResultPathParser : (String) -> Either<IllegalArgumentException, GQLResultPath> {

    override fun invoke(pathAsString: String): Either<IllegalArgumentException, GQLResultPath> {
        return try {
            val uri: URI = URI.create(pathAsString)
            if (GQLResultPath.GQL_RESULT_PATH_SCHEME != uri.scheme) {
                IllegalArgumentException(
                        "scheme of uri does not match expected scheme [ expected: %s, actual: %s ]"
                            .format(GQLResultPath.GQL_RESULT_PATH_SCHEME, uri.scheme)
                    )
                    .left()
            } else {
                GQLResultPath.of(appendPathSegmentsToBuilder(uri.path)).right()
            }
        } catch (t: Throwable) {
            IllegalArgumentException(
                    "unable to create %s from string: [ type: %s, message: %s ]"
                        .format(GQLResultPath::class.simpleName, t::class.simpleName, t.message)
                )
                .left()
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
        return { s: String ->
            val firstStartBracketIndex: Int = s.indexOf('[')
            when {
                firstStartBracketIndex == 0 -> {
                    val firstEndBracketIndex: Int = s.indexOf(']', startIndex = 1)
                    when {
                        firstEndBracketIndex < 0 -> {
                            throw IllegalArgumentException(
                                "start bracket for index within %s not closed with end bracket: [ segment_string: '%s' ]"
                                    .format(ElementSegment::class.simpleName, s)
                            )
                        }
                        firstEndBracketIndex == 1 -> {
                            throw IllegalArgumentException(
                                "no index has been specified within start and end brackets: [ segment_string: '%s' ]"
                                    .format(s)
                            )
                        }
                        firstEndBracketIndex < s.length - 1 -> {
                            throw IllegalArgumentException(
                                "contains text after end bracket: [ segment_string: '%s' ]"
                                    .format(s)
                            )
                        }
                        else -> {
                            val index: Int? = s.substring(1, firstEndBracketIndex).toIntOrNull()
                            when {
                                index == null -> {
                                    throw IllegalArgumentException(
                                        "integral index value could not be parsed from: [ segment_string: '%s' ]"
                                            .format(s)
                                    )
                                }
                                index < 0 -> {
                                    throw IllegalArgumentException(
                                        "index value less than zero extracted for [ segment_string: '%s' ]"
                                            .format(s)
                                    )
                                }
                                else -> {
                                    UnnamedListSegment(index = index)
                                }
                            }
                        }
                    }
                }
                firstStartBracketIndex > 0 -> {
                    val firstEndBracketIndex: Int =
                        s.indexOf(']', startIndex = firstStartBracketIndex + 1)
                    when {
                        firstEndBracketIndex < 0 -> {
                            throw IllegalArgumentException(
                                "start bracket for index within %s not closed with end bracket: [ segment_string: '%s' ]"
                                    .format(ElementSegment::class.simpleName, s)
                            )
                        }
                        firstEndBracketIndex == firstStartBracketIndex + 1 -> {
                            throw IllegalArgumentException(
                                "no index has been specified within start and end brackets: [ segment_string: '%s' ]"
                                    .format(s)
                            )
                        }
                        firstEndBracketIndex < s.length - 1 -> {
                            throw IllegalArgumentException(
                                "contains text after end bracket: [ segment_string: '%s' ]"
                                    .format(s)
                            )
                        }
                        else -> {
                            val index: Int? =
                                s.substring(firstStartBracketIndex + 1, firstEndBracketIndex)
                                    .toIntOrNull()
                            when {
                                index == null -> {
                                    throw IllegalArgumentException(
                                        "integral index value could not be parsed from: [ segment_string: '%s' ]"
                                            .format(s)
                                    )
                                }
                                index < 0 -> {
                                    throw IllegalArgumentException(
                                        "index value less than zero extracted for [ segment_string: '%s' ]"
                                            .format(s)
                                    )
                                }
                                else -> {
                                    NamedListSegment(
                                        name = s.substring(0, firstStartBracketIndex).trimEnd(),
                                        index = index
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    NamedSegment(name = s)
                }
            }
        }
    }
}
