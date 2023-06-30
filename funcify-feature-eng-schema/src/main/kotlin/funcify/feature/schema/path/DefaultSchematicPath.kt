package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * @author smccarron
 * @created 2/20/22
 */
internal data class DefaultSchematicPath(
    override val scheme: String = SchematicPath.GRAPHQL_SCHEMATIC_PATH_SCHEME,
    override val pathSegments: PersistentList<String> = persistentListOf(),
    override val arguments: PersistentMap<String, JsonNode> = persistentMapOf(),
    override val directives: PersistentMap<String, JsonNode> = persistentMapOf()
) : SchematicPath {

    companion object {

        internal data class DefaultBuilder(private val schematicPath: DefaultSchematicPath) :
            SchematicPath.Builder {

            private var inputScheme: String = schematicPath.scheme

            private val pathBuilder: PersistentList.Builder<String> =
                schematicPath.pathSegments.builder()
            private val argsBuilder: PersistentMap.Builder<String, JsonNode> =
                schematicPath.arguments.builder()
            private val dirsBuilder: PersistentMap.Builder<String, JsonNode> =
                schematicPath.directives.builder()

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

            override fun argument(key: String, value: JsonNode): SchematicPath.Builder {
                key.toOption()
                    .map { k -> k.trim() }
                    .filter { s -> s.isNotEmpty() }
                    .tap { k -> argsBuilder[k] = value }
                return this
            }

            override fun argument(keyValuePair: Pair<String, JsonNode>): SchematicPath.Builder {
                keyValuePair.first
                    .toOption()
                    .map { k -> k.trim() }
                    .filter { k -> k.isNotEmpty() }
                    .tap { k -> argsBuilder[k] = keyValuePair.second }
                return this
            }

            override fun arguments(keyValuePairs: Map<String, JsonNode>): SchematicPath.Builder {
                keyValuePairs.asSequence().fold(argsBuilder) { ab, (key, value) ->
                    key.toOption()
                        .map { k -> k.trim() }
                        .filter { s -> s.isNotEmpty() }
                        .fold(
                            { ab },
                            { k ->
                                ab[k] = value
                                ab
                            }
                        )
                }
                return this
            }

            override fun dropArgument(key: String): SchematicPath.Builder {
                if (key in argsBuilder) {
                    argsBuilder.remove(key)
                }
                return this
            }

            override fun clearArguments(): SchematicPath.Builder {
                argsBuilder.clear()
                return this
            }

            override fun directive(key: String, value: JsonNode): SchematicPath.Builder {
                key.toOption()
                    .map { k -> k.trim() }
                    .filter { k -> k.isNotEmpty() }
                    .tap { k -> dirsBuilder[k] = value }
                return this
            }

            override fun directive(keyValuePair: Pair<String, JsonNode>): SchematicPath.Builder {
                keyValuePair.first
                    .toOption()
                    .map { k -> k.trim() }
                    .filter { k -> k.isNotEmpty() }
                    .tap { k -> dirsBuilder[k] = keyValuePair.second }
                return this
            }

            override fun directives(keyValuePairs: Map<String, JsonNode>): SchematicPath.Builder {
                keyValuePairs.asSequence().fold(dirsBuilder) { db, (key, value) ->
                    key.toOption()
                        .map { k -> k.trim() }
                        .filter { k -> k.isNotEmpty() }
                        .fold(
                            { db },
                            { k ->
                                db[k] = value
                                db
                            }
                        )
                }
                return this
            }

            override fun dropDirective(key: String): SchematicPath.Builder {
                if (key in dirsBuilder) {
                    dirsBuilder.remove(key)
                }
                return this
            }

            override fun clearDirectives(): SchematicPath.Builder {
                dirsBuilder.clear()
                return this
            }

            override fun build(): SchematicPath {
                return DefaultSchematicPath(
                    scheme = inputScheme,
                    pathSegments = pathBuilder.build(),
                    arguments = argsBuilder.build(),
                    directives = dirsBuilder.build()
                )
            }
        }
    }

    private val uri: URI by lazy {
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
                if (arguments.isNotEmpty()) {
                    append("?")
                    append(convertJsonNodeMapToURIQueryParamFormat(arguments))
                }
                if (directives.isNotEmpty()) {
                    append("#")
                    append(convertJsonNodeMapToURIQueryParamFormat(directives))
                }
            }
        )
    }

    private fun convertJsonNodeMapToURIQueryParamFormat(
        jsonNodeMap: Map<String, JsonNode>
    ): String {
        return jsonNodeMap
            .asSequence()
            .map { (k: String, v: JsonNode) ->
                when (v) {
                    is NullNode -> {
                        URLEncoder.encode(k, StandardCharsets.UTF_8) to ""
                    }
                    else -> {
                        URLEncoder.encode(k, StandardCharsets.UTF_8) to
                            URLEncoder.encode(v.toString(), StandardCharsets.UTF_8)
                    }
                }
            }
            .joinToString("&") { (k: String, v: String) ->
                when {
                    v.isBlank() -> {
                        k
                    }
                    else -> {
                        "${k}=${v}"
                    }
                }
            }
    }

    private val internedParentPath: Option<SchematicPath> by lazy { super.getParentPath() }

    override fun toURI(): URI {
        return uri
    }

    override fun getParentPath(): Option<SchematicPath> {
        return internedParentPath
    }

    override fun transform(
        mapper: SchematicPath.Builder.() -> SchematicPath.Builder
    ): SchematicPath {
        val builder: SchematicPath.Builder = DefaultBuilder(this)
        return mapper.invoke(builder).build()
    }

    override fun toString(): String {
        return uri.toASCIIString()
    }

    override fun toDecodedURIString(): String {
        val builder = StringBuilder(uri.scheme).append(':').append(uri.path)
        return when {
            arguments.isNotEmpty() && directives.isEmpty() -> builder.append('?').append(uri.query)
            arguments.isEmpty() && directives.isNotEmpty() ->
                builder.append('#').append(uri.fragment)
            arguments.isNotEmpty() && directives.isNotEmpty() ->
                builder.append('?').append(uri.query).append('#').append(uri.fragment)
            else -> builder
        }.toString()
    }
}
