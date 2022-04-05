package funcify.feature.data

import arrow.core.some
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.json.JsonObjectMappingConfiguration
import graphql.introspection.IntrospectionResultToSchema
import graphql.language.SDLNamedDefinition
import org.junit.jupiter.api.Test


/**
 *
 * @author smccarron
 * @created 4/4/22
 */
internal class MockGraphQLFetcherMetadataProviderTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun provideMockMetadataTest() {
        MockGraphQLFetcherMetadataProvider(objectMapper).provideMetadata()
                .blockFirst()
                .fold({ jn ->
                          objectMapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(jn)
                                  .some()
                      },
                      { t: Throwable -> throw t })
                .map { jsonString: String ->
                    objectMapper.readValue<Map<String, Any?>>(content = jsonString)
                }
                .map { strMap: Map<String, Any?> -> IntrospectionResultToSchema().createSchemaDefinition(strMap) }
                .fold({},
                      { document ->
                          println("definitions.size: ${document.definitions.size}")
                          println("definition_names: ${
                              document.definitions.asSequence()
                                      .map { d ->
                                          d.some()
                                                  .map { def -> (def as? SDLNamedDefinition<*>)?.name }
                                      }
                                      .joinToString(",\n",
                                                    "{ ",
                                                    " }")
                          }")
                      })
    }

}