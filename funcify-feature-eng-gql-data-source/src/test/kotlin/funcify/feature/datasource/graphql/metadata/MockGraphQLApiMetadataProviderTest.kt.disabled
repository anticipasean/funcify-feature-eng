package funcify.feature.datasource.graphql.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.metadata.MockGraphQLApiMetadataProvider.Companion.fakeService
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.extensions.MonoExtensions.toTry
import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 4/5/22
 */
internal class MockGraphQLApiMetadataProviderTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun provideMockMetadataTest() {
        MockGraphQLApiMetadataProvider(objectMapper)
            .provideTypeDefinitionRegistry(fakeService)
            .toTry()
            .fold(
                { td: TypeDefinitionRegistry ->
                    Assertions.assertEquals(
                        1,
                        td.getType("Query", ObjectTypeDefinition::class.java)
                            .orElse(null)
                            ?.fieldDefinitions
                            ?.size
                    )
                    Assertions.assertEquals(
                        "shows",
                        td.getType("Query", ObjectTypeDefinition::class.java)
                            .orElse(null)
                            ?.fieldDefinitions
                            ?.get(0)
                            ?.name
                    )
                },
                { thr: Throwable ->
                    Assertions.fail(
                        "an error occurred when attempting to get graphql metadata",
                        thr
                    )
                }
            )
    }
}
