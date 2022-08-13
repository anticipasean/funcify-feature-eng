package funcify.feature.datasource.graphql.retrieval

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.path.SchematicPath
import graphql.language.AstPrinter
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Assertions
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
        /*loggerFor<GraphQLQueryPathBasedComposerTest>()
        .debug("path_set: {}", pathsSet.joinToString(",\n", "{ ", " }"))*/
        val queryOperationComposerFunction:
            (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition =
            GraphQLQueryPathBasedComposer
                .createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
                    pathsSet
                )
        val expectedQuery: String =
            """
            |query {
            |  shows {
            |    artwork {
            |      url
            |    }
            |    releaseYear
            |    reviews {
            |      starScore
            |      submittedDate
            |      username
            |    }
            |    title
            |  }
            |}
        """.trimMargin()
        Assertions.assertEquals(
            expectedQuery,
            AstPrinter.printAst(queryOperationComposerFunction.invoke(persistentMapOf()))
        )
        Assertions.assertTrue(
            AstPrinter.printAst(
                    queryOperationComposerFunction.invoke(
                        persistentMapOf(
                            SchematicPath.of { pathSegment("shows", "title").argument("format") } to
                                JsonNodeFactory.instance.textNode("<name>: <year>")
                        )
                    )
                )
                .contains("title(format: \"<name>: <year>\")"),
            "argument for [ format ] expected but not found"
        )

        Assertions.assertFalse(
            AstPrinter.printAst(
                    queryOperationComposerFunction.invoke(
                        persistentMapOf(
                            SchematicPath.of { pathSegment("shows", "title").argument("format") } to
                                JsonNodeFactory.instance.textNode("<name>: <year>"),
                            SchematicPath.of {
                                pathSegment("shows", "title", "abbreviated_version")
                                    .argument("format")
                            } to JsonNodeFactory.instance.textNode("<name>")
                        )
                    )
                )
                .contains("abbreviated_version"),
            "path_segment [ abbreviated_version ] not included in given source_path set so should not be in output query"
        )
        Assertions.assertTrue(
            AstPrinter.printAst(
                    queryOperationComposerFunction.invoke(
                        persistentMapOf(
                            SchematicPath.of {
                                pathSegment("shows", "releaseYear").argument("between")
                            } to JsonNodeFactory.instance.arrayNode().add(1999).add(2005)
                        )
                    )
                )
                .contains("releaseYear(between: [1999, 2005])"),
            "argument for [ between ] expected but not found"
        )
    }
}
