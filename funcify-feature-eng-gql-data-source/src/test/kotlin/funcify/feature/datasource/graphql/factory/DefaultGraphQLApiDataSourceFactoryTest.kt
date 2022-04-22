package funcify.feature.datasource.graphql.factory

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.metadata.MockGraphQLFetcherMetadataProvider
import funcify.feature.datasource.graphql.reader.DefaultGraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceContainerType
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.configuration.SchemaConfiguration
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.path.SchematicPathFactory
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DefaultGraphQLApiDataSourceFactoryTest {

    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()
    private val gqlDataSourceFactory: GraphQLApiDataSourceFactory =
        DefaultGraphQLApiDataSourceFactory(
            graphQLFetcherMetadataProvider =
                MockGraphQLFetcherMetadataProvider(objectMapper = objectMapper),
            graphQLMetadataReader = DefaultGraphQLApiSourceMetadataReader()
        )

    @Test
    fun createGraphQLApiDataSourceTest() {
        val graphQLApiDataSource: GraphQLApiDataSource =
            gqlDataSourceFactory.createGraphQLApiDataSource(
                "myDataElements",
                MockGraphQLFetcherMetadataProvider.fakeService
            )
        Assertions.assertEquals("myDataElements", graphQLApiDataSource.name)
        Assertions.assertFalse(
            graphQLApiDataSource.graphQLSourceSchema.queryType.fieldDefinitions.isEmpty(),
            "empty graphqlschema"
        )
        Assertions.assertEquals(
            RawDataSourceType.GRAPHQL_API,
            graphQLApiDataSource.sourceType,
            "not graphql_api data source"
        )
        Assertions.assertFalse(
            graphQLApiDataSource.sourceMetamodel.sourceIndicesByPath.isEmpty(),
            "empty source_indices_by_path derived from data_source"
        )
    }

    @Test
    fun createMetamodelGraphFromGraphQLApiDataSourceTest() {
        val graphQLApiDataSource: GraphQLApiDataSource =
            gqlDataSourceFactory.createGraphQLApiDataSource(
                "myDataElements",
                MockGraphQLFetcherMetadataProvider.fakeService
            )
        val schemaConfiguration = SchemaConfiguration()
        val defaultMetamodelGraphFactory =
            schemaConfiguration.metamodelGraphFactory(
                schemaConfiguration.schematicVertexFactory(),
                schemaConfiguration.sourcePathTransformer()
            )
        val metamodelGraphBuildAttempt: Try<MetamodelGraph> =
            try {
                defaultMetamodelGraphFactory.builder().addDataSource(graphQLApiDataSource).build()
            } catch (t: Throwable) {
                Assertions.fail<Try<MetamodelGraph>>(
                    "throwable was not caught in creation of metamodel graph",
                    t
                )
            }
        if (metamodelGraphBuildAttempt.isFailure()) {
            Assertions.fail<Unit>(
                "throwable was not expected in creation of metamodel graph",
                metamodelGraphBuildAttempt.getFailure().orNull()!!
            )
        }
        val metamodelGraph = metamodelGraphBuildAttempt.orNull()!!
        Assertions.assertTrue(
            metamodelGraph.dataSourcesByName.isNotEmpty(),
            "graph should have the gql data source provided"
        )
        val gqlDatasource = metamodelGraph.dataSourcesByName.asIterable().first()
        Assertions.assertEquals(
            graphQLApiDataSource.name,
            gqlDatasource.key,
            "the name for the gql datasource does not match"
        )
        val artworkUrlPath =
            SchematicPathFactory.createPathWithSegments(
                StandardNamingConventions.SNAKE_CASE.deriveName(
                        metamodelGraph.dataSourcesByName.asIterable().first().key
                    )
                    .qualifiedForm,
                "shows",
                "artwork",
                "url"
            )
        Assertions.assertNotNull(
            metamodelGraph.schematicVerticesByPath[artworkUrlPath],
            """expected vertex for path fes:/{data_source_name in snake_case}/shows/artwork/url
                |; if failed, creation of schematic vertices likely failed to recurse into
                |source attributes of source container types
            """.trimMargin()
        )
        Assertions.assertTrue(
            metamodelGraph.schematicVerticesByPath[artworkUrlPath] is LeafVertex,
            """expected artwork url to be leaf vertex within metamodel graph
                |; if failed, composite attributes are likely not being 
                |mapped properly to their graph positions 
            """.trimMargin()
        )
        Assertions.assertTrue(
            metamodelGraph.schematicVerticesByPath[artworkUrlPath.getParentPath().orNull()!!]
                .toOption()
                .filterIsInstance<JunctionVertex>()
                .filter { jv ->
                    jv.compositeAttribute.getSourceAttributeByDataSource()[graphQLApiDataSource] is
                        DefaultGraphQLSourceAttribute &&
                        jv.compositeContainerType.getSourceContainerTypeByDataSource()[
                            graphQLApiDataSource] is
                            DefaultGraphQLSourceContainerType
                }
                .isDefined(),
            """expected artwork to be junction vertex, 
                |having a representation as both container type 
                |and attribute for the given source;
                |if failed, parallelizing might have caused the 
                |composite attribute or container type entry to be overwritten""".flattenIntoOneLine()
        )
        /*val compositeAttributeShowFunc: (CompositeAttribute) -> String = { ca ->
         *          persistentMapOf<String, String>(
         *                  "conventional_name" to ca.conventionalName.toString(),
         *                  "source_attributes_by_datasource" to
         *                      ca.getSourceAttributeByDataSource().mapKeys { ds -> ds.key.name }.toString()
         *              )
         *              .toString()
         *      }
         *      val compositeContainerTypeShowFunc: (CompositeContainerType) -> String = { cct ->
         *          persistentMapOf<String, String>(
         *                  "conventional_name" to cct.conventionalName.toString(),
         *                  "source_containers" to
         *                      cct.getSourceContainerTypeByDataSource()
         *                          .asIterable()
         *                          .map { e ->
         *                              """{ data_source: "${e.key.name}",
         *                              |  source_container: {
         *                              |    name: ${e.value.name.qualifiedForm},
         *                              |    source_path: ${e.value.sourcePath},
         *                              |    source_attributes: ${e.value.sourceAttributes.asIterable().map { sa -> "{ name: ${sa.name}, path: ${sa.sourcePath} }" }.joinToString(",\n\t\t\t", "{ ", " }")}
         *                              |  }
         *                              |}""".trimMargin()
         *                          }
         *                          .joinToString(",\n\t\t", "{\n", "\n}")
         *              )
         *              .toString()
         *      }
         *
         *      metamodelGraph.schematicVerticesByPath.forEach { (sp, sv) ->
         *          val vertexToString: String =
         *              when (sv) {
         *                  is JunctionVertex -> {
         *                      sequenceOf(
         *                              "composite_source_container_type: ${compositeContainerTypeShowFunc.invoke(sv.compositeContainerType)}",
         *                              "composite_source_attribute: ${compositeAttributeShowFunc.invoke(sv.compositeAttribute)}"
         *                          )
         *                          .joinToString(",\n\t", "[\n", "\n]")
         *                  }
         *                  is LeafVertex -> {
         *                      sequenceOf(
         *                              "composite_source_attribute: ${compositeAttributeShowFunc.invoke(sv.compositeAttribute)}"
         *                          )
         *                          .joinToString(",\n\t", "[\n", "\n]")
         *                  }
         *                  else -> {
         *                      sv.toString()
         *                  }
         *              }
         *          println("path: ${sp}, vertex: $vertexToString")
         *      }
         */
    }
}
