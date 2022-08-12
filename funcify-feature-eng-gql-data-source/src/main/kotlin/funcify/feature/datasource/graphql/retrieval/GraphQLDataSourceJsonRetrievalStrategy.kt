package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategy
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-12
 */
internal class GraphQLDataSourceJsonRetrievalStrategy(
    internal val graphQLDataSource: GraphQLApiDataSource,
    override val parameterVertices:
        ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    override val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
) : DataSourceSpecificJsonRetrievalStrategy<GraphQLSourceIndex> {

    companion object {
        private val logger: Logger = loggerFor<GraphQLDataSourceJsonRetrievalStrategy>()
    }

    override val dataSource: DataSource<GraphQLSourceIndex> = graphQLDataSource

    override val parameterPaths: ImmutableSet<SchematicPath> by lazy { super.parameterPaths }

    override val sourcePaths: ImmutableSet<SchematicPath> by lazy { super.sourcePaths }

    init {
        parameterVertices
            .asSequence()
            .filterNot { pjvOrPlv ->
                pjvOrPlv.fold(
                    { pjv ->
                        pjv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { plv ->
                        plv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>()) {
                ps,
                pjvOrPlv ->
                ps.add(pjvOrPlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { pjvOrPlv ->
                                pjvOrPlv.fold(
                                    ParameterJunctionVertex::path,
                                    ParameterLeafVertex::path
                                )
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following parameter_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
        sourceVertices
            .asSequence()
            .filterNot { sjvOrSlv ->
                sjvOrSlv.fold(
                    { sjv ->
                        sjv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { slv ->
                        slv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>()) { ps, sjvOrSlv
                ->
                ps.add(sjvOrSlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { sjvOrSlv ->
                                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following source_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
    }

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        val parameterPathsSetStr = valuesByParameterPaths.keys.joinToString(",", "{ ", " }")
        logger.debug("invoke: [ values_by_parameter_paths.keys: $parameterPathsSetStr ]")
        valuesByParameterPaths.asSequence().filter { (parameterPath, _) ->
            parameterPath in parameterPaths
        }
        TODO("Not yet implemented")
    }

    

}
