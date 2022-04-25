package funcify.feature.datasource.graphql.reader

import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.lastOrNone
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.metadata.MockGraphQLFetcherMetadataProvider
import funcify.feature.datasource.graphql.metadata.MockGraphQLFetcherMetadataProvider.Companion.fakeService
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.extensions.OptionExtensions.stream
import graphql.schema.GraphQLSchema
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class GraphQLApiSourceMetadataReaderTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()

    @Test
    fun readSourceMetamodelFromMetadataTest() {

        val graphQLSchema: GraphQLSchema =
            MockGraphQLFetcherMetadataProvider(objectMapper)
                .provideMetadata(fakeService)
                .blockFirst()
                .fold({ gqls: GraphQLSchema -> gqls }, { t: Throwable -> Assertions.fail(t) })
        val sourceMetamodel =
            DefaultGraphQLApiSourceMetadataReader(DefaultGraphQLSourceIndexFactory())
                .readSourceMetamodelFromMetadata(graphQLSchema)
        /**
         * println(sourceMetamodel.sourceIndicesByPath.asSequence()
         * ```
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
         * ```
         */
        Assertions.assertEquals(11, sourceMetamodel.sourceIndicesByPath.size)

        Assertions.assertEquals(
            "shows",
            sourceMetamodel
                .sourceIndicesByPath
                .entries
                .asIterable()
                .filter { entry ->
                    entry.key.pathSegments.lastOrNone().filter { s -> s == "shows" }.isDefined()
                }
                .firstOrNone()
                .flatMap { entry -> entry.value.firstOrNone() }
                .map { gqlSrcInd -> gqlSrcInd.name.qualifiedForm }
                .getOrElse { "" }
        )

        Assertions.assertEquals(
            5,
            sourceMetamodel
                .sourceIndicesByPath
                .entries
                .asIterable()
                .filter { entry ->
                    entry.key.pathSegments.lastOrNone().filter { s -> s == "shows" }.isDefined()
                }
                .firstOrNone()
                .map { entry -> entry.value }
                .stream()
                .flatMap { set -> set.stream() }
                .filter { i -> i is DefaultGraphQLSourceContainerType }
                .map { i -> i as GraphQLSourceContainerType }
                .map { gqlSrcCont -> gqlSrcCont.sourceAttributes.size }
                .findFirst()
                .orElse(-1)
        )

        /*  sourceMetamodel
         *    .sourceIndicesByPath
         *    .entries
         *    .stream()
         *    .flatMap { e ->
         *        e.value.stream().flatMap { si ->
         *            StreamSupport.stream(
         *                ParentChildPairRecursiveSpliterator<GraphQLSourceIndex>(
         *                    rootValue = si,
         *                    traversalFunction = { gsi: GraphQLSourceIndex ->
         *                        gsi.toOption()
         *                            .filterIsInstance<DefaultGraphQLSourceContainerType>()
         *                            .map { gct ->
         *                                gct.sourceAttributes.stream().map { gsa ->
         *                                    gsa as GraphQLSourceIndex
         *                                }
         *                            }
         *                            .getOrElse { Stream.empty() }
         *                    }
         *                ),
         *                false
         *            )
         *        }
         *    }
         *    .forEach { (p, c) ->
         *        println("parent: $p, child: $c")
         *    }
         */
    }
}
