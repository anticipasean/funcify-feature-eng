package funcify.feature.schema.path

import funcify.feature.schema.SchematicPath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
data class DefaultSchematicPath(val scheme: String = "fes",
                                override val pathSegments: ImmutableList<String> = persistentListOf(),
                                override val arguments: ImmutableMap<String, String> = persistentMapOf(),
                                override val directives: ImmutableMap<String, String> = persistentMapOf()) : SchematicPath {

    private val uri: URI by lazy { createUriFromProperties() }

    private fun createUriFromProperties(): URI {
        return directives.asSequence()
                .fold(arguments.asSequence()
                              .fold(UriComponentsBuilder.newInstance()
                                            .scheme(scheme)
                                            .path(pathSegments.joinToString(separator = "/",
                                                                            prefix = "/")),
                                    { ucb: UriComponentsBuilder, entry: Map.Entry<String, String> ->
                                        ucb.queryParam(entry.key,
                                                       entry.value)
                                    }),
                      { ucb: UriComponentsBuilder, entry: Map.Entry<String, String> ->
                          if (entry.value.isEmpty()) {
                              ucb.fragment(entry.key)
                          } else {
                              ucb.fragment("${entry.key}=${entry.value}")
                          }
                      })
                .build()
                .toUri()
    }

    override fun toURI(): URI {
        return uri
    }

}