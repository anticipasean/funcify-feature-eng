package funcify.feature.datasource.graphql.metadata

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.metadata.MockGraphQLApiMetadataProvider.Companion.fakeService
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.extensions.MonoExtensions.toTry
import graphql.schema.GraphQLSchema
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 4/5/22
 */
internal class MockGraphQLApiMetadataProviderTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun provideMockMetadataTest() {
        MockGraphQLApiMetadataProvider(objectMapper)
            .provideMetadata(fakeService)
            .toTry()
            .fold(
                { gqlSchema: GraphQLSchema ->
                    Assertions.assertEquals(
                        1,
                        gqlSchema.queryType.definition?.fieldDefinitions?.size
                    )
                    Assertions.assertEquals(
                        "shows",
                        gqlSchema.queryType?.definition?.fieldDefinitions?.get(0)?.name
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
