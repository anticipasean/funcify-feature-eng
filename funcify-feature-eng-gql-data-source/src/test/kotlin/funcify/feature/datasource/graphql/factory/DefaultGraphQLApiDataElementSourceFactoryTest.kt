package funcify.feature.datasource.graphql.factory

internal class DefaultGraphQLApiDataElementSourceFactoryTest {
    /*
    private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()
    private val gqlDataSourceFactory: GraphQLApiDataElementSourceProviderFactory =
        DefaultGraphQLApiDataElementSourceProviderFactory(
            graphQLApiMetadataProvider =
                MockGraphQLApiMetadataProvider(objectMapper = objectMapper),
            graphQLApiSourceMetadataReader =
                ComprehensiveGraphQLApiSourceMetadataReader(
                    graphQLSourceIndexFactory = DefaultGraphQLSourceIndexFactory(),
                    graphQLSourceIndexCreationContextFactory =
                        DefaultGraphQLSourceIndexCreationContextFactory,
                    graphQLApiSourceMetadataFilter =
                        InternalServiceTypesExcludingSourceMetadataFilter()
                )
                                                         )

    @Test
    fun createGraphQLApiDataSourceTest() {
        val graphQLApiDataSource: GraphQLApiDataElementSource =
            gqlDataSourceFactory.createGraphQLApiDataSource(
                "myDataElements",
                MockGraphQLApiMetadataProvider.fakeService
            )
        Assertions.assertEquals("myDataElements", graphQLApiDataSource.name)
        Assertions.assertFalse(
            graphQLApiDataSource.graphQLSourceSchema.queryType.fieldDefinitions.isEmpty(),
            "empty graphqlschema"
        )
        Assertions.assertEquals(
            RawSourceType.GRAPHQL_API,
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
        val graphQLApiDataSource: GraphQLApiDataElementSource =
            gqlDataSourceFactory.createGraphQLApiDataSource(
                "myDataElements",
                MockGraphQLApiMetadataProvider.fakeService
            )
        val schemaConfiguration = SchemaConfiguration()
        val defaultMetamodelGraphFactory =
            schemaConfiguration.metamodelGraphFactory(schemaConfiguration.schematicVertexFactory())

        val metamodelGraphBuildAttempt: Try<MetamodelGraph> =
            try {
                defaultMetamodelGraphFactory
                    .builder()
                    .addDataSource(graphQLApiDataSource)
                    .build()
                    .toTry()
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
            metamodelGraph.pathBasedGraph.verticesByPath.isNotEmpty(),
            "graph should have at least one vertex"
        )
        val firstVertexOpt: Option<SchematicVertex> =
            metamodelGraph.pathBasedGraph.verticesByPath.asIterable().first().value.toOption()
        val gqlDatasource: DataElementSource.Key<*>? =
            firstVertexOpt
                .filterIsInstance<SourceRootVertex>()
                .map { v ->
                    v.compositeContainerType
                        .getSourceContainerTypeByDataSource()
                        .asIterable()
                        .first()
                        .key
                }
                .or(
                    firstVertexOpt.filterIsInstance<SourceJunctionVertex>().map { v ->
                        v.compositeContainerType
                            .getSourceContainerTypeByDataSource()
                            .asIterable()
                            .first()
                            .key
                    }
                )
                .or(
                    firstVertexOpt.filterIsInstance<SourceLeafVertex>().map { v ->
                        v.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .asIterable()
                            .first()
                            .key
                    }
                )
                .orNull()
        Assertions.assertEquals(
            graphQLApiDataSource.name,
            gqlDatasource?.name,
            "the name for the gql datasource does not match"
        )
        val artworkUrlPath =
            SchematicPath.getRootPath().transform {
                pathSegment(
                    /*
                     * StandardNamingConventions.SNAKE_CASE.deriveName(graphQLApiDataSource.name)
                     *     .qualifiedForm,
                     */
                    "shows",
                    "artwork",
                    "url"
                )
            }
        Assertions.assertNotNull(
            metamodelGraph.pathBasedGraph.verticesByPath[artworkUrlPath],
            """expected vertex for path fes:/{data_source_name in snake_case}/shows/artwork/url
                |; if failed, creation of schematic vertices likely failed to recurse into
                |source attributes of source container types
            """.trimMargin()
        )
        Assertions.assertTrue(
            metamodelGraph.pathBasedGraph.verticesByPath[artworkUrlPath] is SourceLeafVertex,
            """expected artwork url to be leaf vertex within metamodel graph
                |; if failed, composite attributes are likely not being
                |mapped properly to their graph positions
            """.trimMargin()
        )
        Assertions.assertTrue(
            metamodelGraph.pathBasedGraph.verticesByPath[artworkUrlPath.getParentPath().orNull()!!]
                .toOption()
                .filterIsInstance<SourceJunctionVertex>()
                .filter { jv ->
                    jv.compositeAttribute
                        .getSourceAttributeByDataSource()[graphQLApiDataSource.key] is
                        DefaultGraphQLSourceAttribute &&
                        jv.compositeContainerType
                            .getSourceContainerTypeByDataSource()[graphQLApiDataSource.key] is
                            DefaultGraphQLSourceContainerType
                }
                .isDefined(),
            """expected artwork to be junction vertex,
                |having a representation as both container type
                |and attribute for the given source;
                |if failed, parallelizing might have caused the
                |composite attribute or container type entry to be overwritten""".flatten()
        )
        /*
         *val compositeAttributeShowFunc: (CompositeAttribute) -> String = { ca ->
         *    persistentMapOf<String, String>(
         *            "conventional_name" to ca.conventionalName.toString(),
         *            "source_attributes_by_datasource" to
         *                ca.getSourceAttributeByDataSource().mapKeys { ds -> ds.key.name }.toString()
         *        )
         *        .toString()
         *}
         *val compositeContainerTypeShowFunc: (CompositeContainerType) -> String = { cct ->
         *    persistentMapOf<String, String>(
         *            "conventional_name" to cct.conventionalName.toString(),
         *            "source_containers" to
         *                cct.getSourceContainerTypeByDataSource()
         *                    .asIterable()
         *                    .map { e ->
         *                        """{ data_source: "${e.key.name}",
         *                               |  source_container: {
         *                               |    name: ${e.value.name.qualifiedForm},
         *                               |    source_path: ${e.value.sourcePath},
         *                               |    source_attributes: ${e.value.sourceAttributes.asIterable().map { sa -> "{ name: ${sa.name}, path: ${sa.sourcePath} }" }.joinToString(",\n\t\t\t", "{ ", " }")}
         *                               |  }
         *                               |}""".trimMargin()
         *                    }
         *                    .joinToString(",\n\t\t", "{\n", "\n}")
         *        )
         *        .toString()
         *}
         *
         *metamodelGraph.verticesByPath.forEach { (sp, sv) ->
         *    val vertexToString: String =
         *        when (sv) {
         *            is JunctionVertex -> {
         *                sequenceOf(
         *                        "composite_source_container_type: ${compositeContainerTypeShowFunc.invoke(sv.compositeContainerType)}",
         *                        "composite_source_attribute: ${compositeAttributeShowFunc.invoke(sv.compositeAttribute)}"
         *                    )
         *                    .joinToString(",\n\t", "[\n", "\n]")
         *            }
         *            is LeafVertex -> {
         *                sequenceOf(
         *                        "composite_source_attribute: ${compositeAttributeShowFunc.invoke(sv.compositeAttribute)}"
         *                    )
         *                    .joinToString(",\n\t", "[\n", "\n]")
         *            }
         *            else -> {
         *                sv.toString()
         *            }
         *        }
         *    println("path: ${sp}, vertex: $vertexToString")
         * }
         */
    }

    */
}
