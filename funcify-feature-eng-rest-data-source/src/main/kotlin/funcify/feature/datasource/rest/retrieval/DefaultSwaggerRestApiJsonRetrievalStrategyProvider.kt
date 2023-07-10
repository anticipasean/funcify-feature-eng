package funcify.feature.datasource.rest.retrieval

internal class DefaultSwaggerRestApiJsonRetrievalStrategyProvider
    /*(
        private val jsonMapper: JsonMapper,
        private val postProcessingStrategy: SwaggerRestApiJsonResponsePostProcessingStrategy
    ) : SwaggerRestApiJsonRetrievalStrategyProvider {

        companion object {
            private val logger: Logger = loggerFor<DefaultSwaggerRestApiJsonRetrievalStrategyProvider>()
        }

        override fun providesJsonValueRetrieversForVerticesWithSourceIndicesIn(
            dataSourceKey: DataElementSource.Key<*>
        ): Boolean {
            return dataSourceKey.sourceIndexType.isSubclassOf(RestApiSourceIndex::class)
        }

        override fun createExternalDataSourceJsonValuesRetrieverFor(
            dataSource: DataElementSource<RestApiSourceIndex>,
            sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>,
            parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
        ): Try<DataElementJsonValueSource> {
            logger.debug(
                """create_schematic_path_based_json_retrieval_function_for: [
                |data_source: ${dataSource.key},
                |source_vertices.size: ${sourceVertices.size},
                |parameter_vertices.size: ${parameterVertices.size}
                |]""".flatten()
            )
            return Try.attempt {
                DefaultSwaggerRestDataElementJsonRetrievalStrategy(
                    jsonMapper = jsonMapper,
                    dataSource = dataSource as RestApiDataElementSource,
                    parameterVertices = parameterVertices,
                    sourceVertices = sourceVertices,
                    postProcessingStrategy = postProcessingStrategy
                                                                  )
            }
        }
    }*/
