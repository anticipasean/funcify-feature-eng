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
                    .map { s: String ->
                        when {
                            s.indexOf('[') == 0 -> {
                                val fragmentDelimiter: Int = s.indexOf(']')
                                val typeNameDelimiter: Int = s.indexOf(':')
                                when {
                                    fragmentDelimiter < 0 -> {
                                        throw IllegalArgumentException(
                                            """fragment start '[' within path segment [ %s ] 
                                                |but no fragment end ']'"""
                                                .flatten()
                                                .format(s)
                                        )
                                    }
                                    typeNameDelimiter < 0 -> {
                                        InlineFragment(
                                            typeName = s.substring(1, fragmentDelimiter).trim(),
                                            fieldName =
                                                s.substring(fragmentDelimiter + 1, s.length)
                                                    .trimStart()
                                        )
                                    }
                                    else -> {
                                        FragmentSpread(
                                            fragmentName = s.substring(1, typeNameDelimiter).trim(),
                                            typeName =
                                                s.substring(
                                                        typeNameDelimiter + 1,
                                                        fragmentDelimiter
                                                    )
                                                    .trim(),
                                            fieldName =
                                                s.substring(fragmentDelimiter + 1, s.length)
                                                    .trimStart()
                                        )
                                    }
                                }
                            }
                            else -> {
                                Field(fieldName = s)
                            }
                        }
                    }
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
}
