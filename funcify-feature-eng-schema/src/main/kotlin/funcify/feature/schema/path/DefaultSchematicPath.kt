package funcify.feature.schema.path

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
    val scheme: String = "fes",
    override val pathSegments: PersistentList<String> = persistentListOf(),
    override val arguments: PersistentMap<String, String> = persistentMapOf(),
    override val directives: PersistentMap<String, String> = persistentMapOf()
) : SchematicPath {

    companion object {

        data class DefaultBuilder(private val schematicPath: DefaultSchematicPath) :
            SchematicPath.Builder {

            private val pathBuilder: PersistentList.Builder<String> =
                schematicPath.pathSegments.builder()
            private val argsBuilder: PersistentMap.Builder<String, String> =
                schematicPath.arguments.builder()
            private val dirsBuilder: PersistentMap.Builder<String, String> =
                schematicPath.directives.builder()

            override fun prependPathSegment(vararg pathSegment: String): SchematicPath.Builder {
                return prependPathSegments(pathSegment.toList())
            }

            override fun prependPathSegments(pathSegments: List<String>): SchematicPath.Builder {
                pathBuilder.addAll(0, pathSegments)
                return this
            }

            override fun dropPathSegment(): SchematicPath.Builder {
                if (pathBuilder.isNotEmpty()) {
                    pathBuilder.removeLast()
                }
                return this
            }

            override fun pathSegment(vararg pathSegment: String): SchematicPath.Builder {
                pathBuilder.addAll(pathSegment)
                return this
            }

            override fun pathSegments(pathSegments: List<String>): SchematicPath.Builder {
                pathBuilder.addAll(pathSegments)
                return this
            }

            override fun clearPathSegments(): SchematicPath.Builder {
                pathBuilder.clear()
                return this
            }

            override fun argument(key: String, value: String): SchematicPath.Builder {
                argsBuilder[key] = value
                return this
            }

            override fun argument(keyValuePair: Pair<String, String>): SchematicPath.Builder {
                argsBuilder[keyValuePair.first] = keyValuePair.second
                return this
            }

            override fun arguments(keyValuePairs: Map<String, String>): SchematicPath.Builder {
                argsBuilder.putAll(keyValuePairs)
                return this
            }

            override fun clearArguments(): SchematicPath.Builder {
                argsBuilder.clear()
                return this
            }

            override fun directive(key: String, value: String): SchematicPath.Builder {
                dirsBuilder[key] = value
                return this
            }

            override fun directive(keyValuePair: Pair<String, String>): SchematicPath.Builder {
                dirsBuilder[keyValuePair.first] = keyValuePair.second
                return this
            }

            override fun directive(keyValuePairs: Map<String, String>): SchematicPath.Builder {
                dirsBuilder.putAll(keyValuePairs)
                return this
            }

            override fun clearDirectives(): SchematicPath.Builder {
                dirsBuilder.clear()
                return this
            }

            override fun build(): SchematicPath {
                return DefaultSchematicPath(
                    scheme = schematicPath.scheme,
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
                        { ucb: UriComponentsBuilder, entry: Map.Entry<String, String> ->
                            ucb.queryParam(entry.key, entry.value)
                        }
                    ),
                { ucb: UriComponentsBuilder, entry: Map.Entry<String, String> ->
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
        return uri.toString()
    }
}
