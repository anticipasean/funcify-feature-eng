package funcify.feature.schema.path

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
import kotlinx.collections.immutable.toPersistentList

/**
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultSchematicPath(
    override val scheme: String = SchematicPath.GRAPHQL_SCHEMATIC_PATH_SCHEME,
    override val pathSegments: PersistentList<String> = persistentListOf(),
    override val argument: Option<Pair<String, PersistentList<String>>> = none(),
    override val directive: Option<Pair<String, PersistentList<String>>> = none()
) : SchematicPath {

    companion object {

        internal data class DefaultBuilder(private val schematicPath: DefaultSchematicPath) :
            SchematicPath.Builder {

            private var inputScheme: String = schematicPath.scheme
            private val pathBuilder: PersistentList.Builder<String> =
                schematicPath.pathSegments.builder()
            private var argumentName: String? = schematicPath.argument.orNull()?.first
            private var argumentPathBuilder: PersistentList.Builder<String> =
                schematicPath.argument.orNull()?.second?.builder()
                    ?: persistentListOf<String>().builder()
            private var directiveName: String? = schematicPath.directive.orNull()?.first
            private var directivePathBuilder: PersistentList.Builder<String> =
                schematicPath.directive.orNull()?.second?.builder()
                    ?: persistentListOf<String>().builder()

            override fun scheme(scheme: String): SchematicPath.Builder {
                inputScheme =
                    scheme
                        .toOption()
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .getOrElse { inputScheme }
                return this
            }

            override fun prependPathSegment(vararg pathSegment: String): SchematicPath.Builder {
                pathSegment
                    .asSequence()
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() }
                    .fold(pathBuilder) { pb, ps ->
                        pb.add(0, ps)
                        pb
                    }
                return this
            }

            override fun prependPathSegments(pathSegments: List<String>): SchematicPath.Builder {
                pathSegments
                    .asSequence()
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() }
                    .fold(pathBuilder) { pb, ps ->
                        pb.add(0, ps)
                        pb
                    }
                return this
            }

            override fun dropPathSegment(): SchematicPath.Builder {
                if (pathBuilder.isNotEmpty()) {
                    pathBuilder.removeLast()
                }
                return this
            }

            override fun pathSegment(vararg pathSegment: String): SchematicPath.Builder {
                pathSegment
                    .asSequence()
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() }
                    .fold(pathBuilder) { pb, ps ->
                        pb.add(ps)
                        pb
                    }
                return this
            }

            override fun pathSegments(pathSegments: List<String>): SchematicPath.Builder {
                pathSegments
                    .asSequence()
                    .map { s -> s.trim() }
                    .filter { s -> s.isNotEmpty() }
                    .fold(pathBuilder) { pb, ps ->
                        pb.add(ps)
                        pb
                    }
                return this
            }

            override fun clearPathSegments(): SchematicPath.Builder {
                pathBuilder.clear()
                return this
            }

            override fun argument(name: String, pathSegments: List<String>): SchematicPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.argumentName = trimmedName
                        this.argumentPathBuilder =
                            pathSegments
                                .asSequence()
                                .map { s -> s.trim() }
                                .filter { s -> s.isNotEmpty() }
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun argument(name: String, vararg pathSegment: String): SchematicPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.argumentName = trimmedName
                        this.argumentPathBuilder =
                            pathSegment
                                .asSequence()
                                .map { s -> s.trim() }
                                .filter { s -> s.isNotEmpty() }
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun prependArgumentPathSegment(
                vararg pathSegment: String
            ): SchematicPath.Builder {
                if (this.argumentName != null && pathSegment.isNotEmpty()) {
                    (pathSegment.size - 1)
                        .downTo(0)
                        .asSequence()
                        .map { i: Int -> pathSegment[i] }
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(this.argumentPathBuilder) { apb, s ->
                            apb.add(0, s)
                            apb
                        }
                }
                return this
            }

            override fun prependArgumentPathSegments(
                pathSegments: List<String>
            ): SchematicPath.Builder {
                if (this.argumentName != null && pathSegments.isNotEmpty()) {
                    this.argumentPathBuilder.addAll(
                        0,
                        pathSegments
                            .asSequence()
                            .map { s -> s.trim() }
                            .filter { s -> s.isNotEmpty() }
                            .toList()
                    )
                }
                return this
            }

            override fun appendArgumentPathSegment(
                vararg pathSegment: String
            ): SchematicPath.Builder {
                if (this.argumentName != null) {
                    pathSegment
                        .asSequence()
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(argumentPathBuilder) { apb, s ->
                            apb.add(s)
                            apb
                        }
                }
                return this
            }

            override fun appendArgumentPathSegments(
                pathSegments: List<String>
            ): SchematicPath.Builder {
                if (this.argumentName != null) {
                    pathSegments
                        .asSequence()
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(argumentPathBuilder) { apb, s ->
                            apb.add(s)
                            apb
                        }
                }
                return this
            }

            override fun dropArgumentPathSegment(): SchematicPath.Builder {
                if (this.argumentName != null && this.argumentPathBuilder.isNotEmpty()) {
                    this.argumentPathBuilder.removeLast()
                }
                return this
            }

            override fun clearArgument(): SchematicPath.Builder {
                this.argumentName = null
                this.argumentPathBuilder.clear()
                return this
            }

            override fun directive(
                name: String,
                pathSegments: List<String>
            ): SchematicPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.directiveName = trimmedName
                        this.directivePathBuilder =
                            pathSegments
                                .asSequence()
                                .map { s -> s.trim() }
                                .filter { s -> s.isNotEmpty() }
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun directive(
                name: String,
                vararg pathSegment: String
            ): SchematicPath.Builder {
                return when (val trimmedName: String = name.trim()) {
                    "" -> {
                        this
                    }
                    else -> {
                        this.directiveName = trimmedName
                        this.directivePathBuilder =
                            pathSegment
                                .asSequence()
                                .map { s -> s.trim() }
                                .filter { s -> s.isNotEmpty() }
                                .toPersistentList()
                                .builder()
                        this
                    }
                }
            }

            override fun prependDirectivePathSegment(
                vararg pathSegment: String
            ): SchematicPath.Builder {
                if (this.directiveName != null && pathSegment.isNotEmpty()) {
                    (pathSegment.size - 1)
                        .downTo(0)
                        .asSequence()
                        .map { i: Int -> pathSegment[i] }
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(directivePathBuilder) { dpb, s ->
                            dpb.add(0, s)
                            dpb
                        }
                }
                return this
            }

            override fun prependDirectivePathSegments(
                pathSegments: List<String>
            ): SchematicPath.Builder {
                if (this.directiveName != null && pathSegments.isNotEmpty()) {
                    this.directivePathBuilder.addAll(
                        0,
                        pathSegments
                            .asSequence()
                            .map { s -> s.trim() }
                            .filter { s -> s.isNotEmpty() }
                            .toList()
                    )
                }
                return this
            }

            override fun appendDirectivePathSegment(
                vararg pathSegment: String
            ): SchematicPath.Builder {
                if (this.directiveName != null) {
                    pathSegment
                        .asSequence()
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(directivePathBuilder) { dpb, s ->
                            dpb.add(s)
                            dpb
                        }
                }
                return this
            }

            override fun appendDirectivePathSegments(
                pathSegments: List<String>
            ): SchematicPath.Builder {
                if (this.directiveName != null) {
                    pathSegments
                        .asSequence()
                        .map { s -> s.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(directivePathBuilder) { dpb, s ->
                            dpb.add(s)
                            dpb
                        }
                }
                return this
            }

            override fun dropDirectivePathSegment(): SchematicPath.Builder {
                if (directiveName != null && directivePathBuilder.isNotEmpty()) {
                    directivePathBuilder.removeLast()
                }
                return this
            }

            override fun clearDirective(): SchematicPath.Builder {
                this.directiveName = null
                this.directivePathBuilder.clear()
                return this
            }

            override fun build(): SchematicPath {
                return DefaultSchematicPath(
                    scheme = inputScheme,
                    pathSegments = pathBuilder.build(),
                    argument =
                        when (argumentName) {
                            null -> {
                                none()
                            }
                            else -> {
                                (argumentName!! to argumentPathBuilder.build()).some()
                            }
                        },
                    directive =
                        when (directiveName) {
                            null -> {
                                none()
                            }
                            else -> {
                                (directiveName!! to directivePathBuilder.build()).some()
                            }
                        }
                )
            }
        }
    }

    private val internedURI: URI by lazy {
        URI.create(
            buildString {
                append(scheme)
                append(':')
                append(
                    pathSegments
                        .asSequence()
                        .map { ps: String -> URLEncoder.encode(ps, StandardCharsets.UTF_8) }
                        .joinToString("/", "/")
                )
                if (argument.isDefined()) {
                    append("?")
                    append(argument.orNull()?.first)
                    if (argument.orNull()?.second?.isNotEmpty() == true) {
                        append("=")
                        append(
                            (argument.orNull()?.second?.asSequence() ?: emptySequence())
                                .map { ps: String -> URLEncoder.encode(ps, StandardCharsets.UTF_8) }
                                .joinToString("/", "/")
                        )
                    }
                }
                if (directive.isDefined()) {
                    append("#")
                    append(directive.orNull()?.first)
                    if (directive.orNull()?.second?.isNotEmpty() == true) {
                        append("=")
                        append(
                            (directive.orNull()?.second?.asSequence() ?: emptySequence())
                                .map { ps: String -> URLEncoder.encode(ps, StandardCharsets.UTF_8) }
                                .joinToString("/", "/")
                        )
                    }
                }
            }
        )
    }

    private val internedParentPath: Option<SchematicPath> by lazy { super.getParentPath() }

    override fun toURI(): URI {
        return internedURI
    }

    override fun getParentPath(): Option<SchematicPath> {
        return internedParentPath
    }

    override fun transform(
        mapper: SchematicPath.Builder.() -> SchematicPath.Builder
    ): SchematicPath {
        return mapper.invoke(DefaultBuilder(this)).build()
    }

    override fun toString(): String {
        return internedURI.toASCIIString()
    }

    override fun toDecodedURIString(): String {
        return buildString {
            append(scheme)
            append(':')
            append(pathSegments.asSequence().joinToString("/", "/"))
            if (argument.isDefined()) {
                append("?")
                append(argument.orNull()?.first)
                if (argument.orNull()?.second?.isNotEmpty() == true) {
                    append("=")
                    append(
                        (argument.orNull()?.second?.asSequence() ?: emptySequence()).joinToString(
                            "/",
                            "/"
                        )
                    )
                }
            }
            if (directive.isDefined()) {
                append("#")
                append(directive.orNull()?.first)
                if (directive.orNull()?.second?.isNotEmpty() == true) {
                    append("=")
                    append(
                        (directive.orNull()?.second?.asSequence() ?: emptySequence()).joinToString(
                            "/",
                            "/"
                        )
                    )
                }
            }
        }
    }
}
