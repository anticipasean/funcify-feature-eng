package funcify.feature.schema.path

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import java.net.URI
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.springframework.web.util.UriComponentsBuilder

/**
 *
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

    private val uri: URI by lazy { createUriFromProperties() }

    private fun createUriFromProperties(): URI {
        return directives
            .asSequence()
            .fold(
                arguments
                    .asSequence()
                    .fold(
                        UriComponentsBuilder.newInstance()
                            .scheme(scheme)
                            .path(pathSegments.joinToString(separator = "/", prefix = "/")),
                        { ucb: UriComponentsBuilder, entry: Map.Entry<String, JsonNode> ->
                            if (entry.value.isNull) {
                                ucb.queryParam(entry.key)
                            } else {
                                ucb.queryParam(entry.key, entry.value)
                            }
                        }
                    ),
                { ucb: UriComponentsBuilder, entry: Map.Entry<String, JsonNode> ->
                    if (entry.value.isEmpty()) {
                        ucb.fragment(entry.key)
                    } else {
                        ucb.fragment("${entry.key}=${entry.value}")
                    }
                }
            )
            .build()
            .toUri()
    }

    override fun toURI(): URI {
        return uri
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
}
