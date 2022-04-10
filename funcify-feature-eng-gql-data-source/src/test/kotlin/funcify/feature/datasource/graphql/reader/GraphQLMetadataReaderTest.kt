package funcify.feature.datasource.graphql.reader

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.lastOrNone
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.metadata.MockGraphQLFetcherMetadataProvider
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.json.JsonObjectMappingConfiguration
import graphql.schema.GraphQLSchema
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class GraphQLMetadataReaderTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun readSourceMetamodelFromMetadataTest() {
        val graphQLSchema: GraphQLSchema = MockGraphQLFetcherMetadataProvider(objectMapper).provideMetadata()
                .blockFirst()
                .fold({ gqls: GraphQLSchema -> gqls },
                      { t: Throwable ->
                          Assertions.fail(t)
                      })
        val sourceMetamodel = GraphQLMetadataReader().readSourceMetamodelFromMetadata(graphQLSchema)
        /**
         * println(sourceMetamodel.sourceIndicesByPath.asSequence()
         *                 .joinToString(separator = ",\n",
         *                               prefix = "{ ",
         *                               postfix = " }",
         *                               transform = { entry: Map.Entry<SchematicPath, ImmutableSet<GraphQLSourceIndex>> ->
         *                                   "path: ${entry.key.toURI()}, indices: ${
         *                                       entry.value.joinToString(",\n",
         *                                                                "{ ",
         *                                                                " }")
         *                                   }"
         *                               }))
         */

        /**
         * type Show {
         *    id: Int!
         *    title(format: TitleFormat): String!
         *    releaseYear: Int
         *    reviews: [Review]
         *    artwork: [Image]
         * }
         * [ Path("../shows"), Path("../shows/reviews"), Path("../shows/artwork") ] => 3
         */
        Assertions.assertEquals(3,
                                sourceMetamodel.sourceIndicesByPath.size)
        /**
         * type Query {
         *    shows(titleFilter: String): [Show]
         * }
         * sourceIndicesByPath[Path(".../shows")] => Set({ shows (as a container), shows (as an attribute on Query/root) }) => 2
         * 1 entry for "shows"
         */
        Assertions.assertEquals("shows",
                                sourceMetamodel.sourceIndicesByPath.entries.asIterable()
                                        .filter { entry ->
                                            entry.key.pathSegments.lastOrNone()
                                                    .filter { s -> s == "shows" }
                                                    .isDefined()
                                        }
                                        .firstOrNone()
                                        .flatMap { entry -> entry.value.firstOrNone() }
                                        .map { gqlSrcInd -> gqlSrcInd.name.qualifiedForm }
                                        .getOrElse { "" })
        /**
         * type Show {
         *   id: Int!
         *   title(format: TitleFormat): String!
         *   releaseYear: Int
         *   reviews: \[Review]
         *   artwork: \[Image]
         * }
         * [id,
         *  title,
         *  releaseYear,
         *  reviews,
         *  artwork]  =>  5
         */
        Assertions.assertEquals(5,
                                sourceMetamodel.sourceIndicesByPath.entries.asIterable()
                                        .filter { entry ->
                                            entry.key.pathSegments.lastOrNone()
                                                    .filter { s -> s == "shows" }
                                                    .isDefined()
                                        }
                                        .firstOrNone()
                                        .flatMap { entry -> entry.value.firstOrNone() }
                                        .filterIsInstance<GraphQLSourceContainerType>()
                                        .map { gqlSrcCont -> gqlSrcCont.sourceAttributes.size }
                                        .getOrElse { -1 })
    }
}