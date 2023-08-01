package funcify.feature.schema.path

import funcify.feature.tools.container.attempt.Try
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
            builder.pathSegments(uri.path.split('/'))
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
