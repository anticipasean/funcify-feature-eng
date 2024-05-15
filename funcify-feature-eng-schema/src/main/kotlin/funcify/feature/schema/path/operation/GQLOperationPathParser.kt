package funcify.feature.schema.path.operation

import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.net.URI

internal object GQLOperationPathParser : (String) -> Try<GQLOperationPath> {

    fun fromURI(inputUri: URI): Try<GQLOperationPath> {
        return Try.attempt { GQLOperationPath.of(extractPathComponentsFromUri(inputUri)) }
    }

    override fun invoke(input: String): Try<GQLOperationPath> {
        return Try.attempt { URI.create(input) }
            .map { uri: URI -> GQLOperationPath.of(extractPathComponentsFromUri(uri)) }
    }

    private fun extractPathComponentsFromUri(
        uri: URI
    ): (GQLOperationPath.Builder) -> GQLOperationPath.Builder {
        return { builder: GQLOperationPath.Builder ->
            builder.scheme(uri.scheme)
            builder.selections(
                uri.path
                    .splitToSequence('/')
                    .filter(String::isNotBlank)
                    .map(String::trim)
                    .map(createSelectionSegmentForPathSegment())
                    .toList()
            )
            if (uri.query?.isNotEmpty() == true) {
                val args: List<String> = uri.query.split('=')
                when (args.size) {
                    1 -> {
                        builder.argument(args[0])
                    }
                    2 -> {
                        builder.argument(args[0], args[1].split('/'))
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "too many arguments provided: [ %s ]".format(uri.query)
                        )
                    }
                }
            }
            if (uri.fragment?.isNotEmpty() == true) {
                val dirs: List<String> = uri.fragment.split('=')
                when (dirs.size) {
                    1 -> {
                        builder.directive(dirs[0])
                    }
                    2 -> {
                        builder.directive(dirs[0], dirs[1].split('/'))
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "too many directives provided: [ %s ]".format(uri.fragment)
                        )
                    }
                }
            }
            builder
        }
    }

    private fun createSelectionSegmentForPathSegment(): (String) -> SelectionSegment {
        return { s: String ->
            when {
                s.indexOf('[') == 0 -> {
                    val firstEndBracket: Int = s.indexOf(']')
                    val firstColon: Int = s.indexOf(':')
                    when {
                        firstEndBracket < 0 -> {
                            throw IllegalArgumentException(
                                """fragment start '[' within path segment [ %s ] 
                                |but no fragment end ']'"""
                                    .flatten()
                                    .format(s)
                            )
                        }
                        firstColon < 0 && firstEndBracket == s.length - 1 -> {
                            InlineFragmentSegment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField = FieldSegment(fieldName = "")
                            )
                        }
                        firstColon < 0 -> {
                            InlineFragmentSegment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    FieldSegment(
                                        fieldName =
                                            s.substring(firstEndBracket + 1, s.length).trimStart()
                                    )
                            )
                        }
                        firstColon < firstEndBracket && firstEndBracket == s.length - 1 -> {
                            FragmentSpreadSegment(
                                fragmentName = s.substring(1, firstColon).trim(),
                                typeName = s.substring(firstColon + 1, firstEndBracket).trim(),
                                selectedField = FieldSegment(fieldName = "")
                            )
                        }
                        firstColon < firstEndBracket -> {
                            val selectedFieldComponent: String =
                                s.substring(firstEndBracket + 1, s.length).trimStart()
                            val secondColon: Int = selectedFieldComponent.indexOf(':')
                            when {
                                secondColon < 0 -> {
                                    FragmentSpreadSegment(
                                        fragmentName = s.substring(1, firstColon).trim(),
                                        typeName =
                                            s.substring(firstColon + 1, firstEndBracket).trim(),
                                        selectedField =
                                            FieldSegment(fieldName = selectedFieldComponent)
                                    )
                                }
                                else -> {
                                    FragmentSpreadSegment(
                                        fragmentName = s.substring(1, firstColon).trim(),
                                        typeName =
                                            s.substring(firstColon + 1, firstEndBracket).trim(),
                                        selectedField =
                                            AliasedFieldSegment(
                                                alias =
                                                    selectedFieldComponent.substring(
                                                        0,
                                                        secondColon
                                                    ),
                                                fieldName =
                                                    selectedFieldComponent.substring(
                                                        secondColon + 1,
                                                        selectedFieldComponent.length
                                                    )
                                            )
                                    )
                                }
                            }
                        }
                        firstColon > firstEndBracket && firstColon == s.length - 1 -> {
                            InlineFragmentSegment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    AliasedFieldSegment(
                                        alias =
                                            s.substring(firstEndBracket + 1, firstColon).trimEnd(),
                                        fieldName = ""
                                    )
                            )
                        }
                        else -> {
                            InlineFragmentSegment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    AliasedFieldSegment(
                                        alias =
                                            s.substring(firstEndBracket + 1, firstColon).trimEnd(),
                                        fieldName =
                                            s.substring(firstColon + 1, s.length).trimStart()
                                    )
                            )
                        }
                    }
                }
                s.indexOf(':') >= 0 -> {
                    when (val firstColon: Int = s.indexOf(':')) {
                        s.length - 1 -> {
                            AliasedFieldSegment(
                                alias = s.substring(0, firstColon).trimEnd(),
                                fieldName = ""
                            )
                        }
                        else -> {
                            AliasedFieldSegment(
                                alias = s.substring(0, firstColon).trimEnd(),
                                fieldName = s.substring(firstColon + 1, s.length).trimStart()
                            )
                        }
                    }
                }
                else -> {
                    FieldSegment(fieldName = s)
                }
            }
        }
    }
}
