package funcify.feature.schema.path

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
                    .map(String::trim)
                    .filter(String::isNotBlank)
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
                            InlineFragment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField = Field(fieldName = "")
                            )
                        }
                        firstColon < 0 -> {
                            InlineFragment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    Field(
                                        fieldName =
                                            s.substring(firstEndBracket + 1, s.length).trimStart()
                                    )
                            )
                        }
                        firstColon < firstEndBracket && firstEndBracket == s.length - 1 -> {
                            FragmentSpread(
                                fragmentName = s.substring(1, firstColon).trim(),
                                typeName = s.substring(firstColon + 1, firstEndBracket).trim(),
                                selectedField = Field(fieldName = "")
                            )
                        }
                        firstColon < firstEndBracket -> {
                            val selectedFieldComponent: String =
                                s.substring(firstEndBracket + 1, s.length).trimStart()
                            val secondColon: Int = selectedFieldComponent.indexOf(':')
                            when {
                                secondColon < 0 -> {
                                    FragmentSpread(
                                        fragmentName = s.substring(1, firstColon).trim(),
                                        typeName =
                                            s.substring(firstColon + 1, firstEndBracket).trim(),
                                        selectedField = Field(fieldName = selectedFieldComponent)
                                    )
                                }
                                else -> {
                                    FragmentSpread(
                                        fragmentName = s.substring(1, firstColon).trim(),
                                        typeName =
                                            s.substring(firstColon + 1, firstEndBracket).trim(),
                                        selectedField =
                                            AliasedField(
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
                            InlineFragment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    AliasedField(
                                        alias =
                                            s.substring(firstEndBracket + 1, firstColon).trimEnd(),
                                        fieldName = ""
                                    )
                            )
                        }
                        else -> {
                            InlineFragment(
                                typeName = s.substring(1, firstEndBracket).trim(),
                                selectedField =
                                    AliasedField(
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
                            AliasedField(
                                alias = s.substring(0, firstColon).trimEnd(),
                                fieldName = ""
                            )
                        }
                        else -> {
                            AliasedField(
                                alias = s.substring(0, firstColon).trimEnd(),
                                fieldName = s.substring(firstColon + 1, s.length).trimStart()
                            )
                        }
                    }
                }
                else -> {
                    Field(fieldName = s)
                }
            }
        }
    }
}
