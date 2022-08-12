package funcify.feature.datasource.graphql.retrieval

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Test

internal class GraphQLQueryPathBasedComposerTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun createQueryTemplateTest() {
        val pathsSet =
            sequenceOf(
                    SchematicPath.of { pathSegment("shows", "title") },
                    SchematicPath.of { pathSegment("shows", "releaseYear") },
                    SchematicPath.of { pathSegment("shows", "reviews", "username") },
                    SchematicPath.of { pathSegment("shows", "reviews", "starScore") },
                    SchematicPath.of { pathSegment("shows", "reviews", "submittedDate") },
                    SchematicPath.of { pathSegment("shows", "artwork", "url") },
                    SchematicPath.of { pathSegment("shows", "title").argument("format") },
                    SchematicPath.of { pathSegment("shows").argument("titleFilter") }
                )
                .toPersistentSet()
        println(
            GraphQLQueryPathBasedComposer.createQueryCompositionFunction(pathsSet)
                .invoke(persistentMapOf())
        )
    }
}
