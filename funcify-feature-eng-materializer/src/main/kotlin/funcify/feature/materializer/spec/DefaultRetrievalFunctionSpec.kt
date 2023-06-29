package funcify.feature.materializer.spec

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultRetrievalFunctionSpec(
    override val dataSource: DataElementSource<*>,
    override val sourceVerticesByPath:
        PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>> =
        persistentMapOf(),
    override val parameterVerticesByPath:
        PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>> =
        persistentMapOf(),
) : RetrievalFunctionSpec {

    companion object {
        private class DefaultSpecBuilder(
            private var dataSource: DataElementSource<*>,
            private val sourceVerticesByPathBuilder:
                PersistentMap.Builder<
                    SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>,
            private val parameterVerticesByPathBuilder:
                PersistentMap.Builder<
                    SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>
        ) : RetrievalFunctionSpec.SpecBuilder {

            override fun dataSource(dataSource: DataElementSource<*>): RetrievalFunctionSpec.SpecBuilder {
                this.dataSource = dataSource
                return this
            }

            override fun addSourceVertex(
                sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
            ): RetrievalFunctionSpec.SpecBuilder {
                this.sourceVerticesByPathBuilder.put(
                    sourceJunctionOrLeafVertex.fold(
                        SourceJunctionVertex::path,
                        SourceLeafVertex::path
                    ),
                    sourceJunctionOrLeafVertex
                )
                return this
            }

            override fun addSourceVertex(
                sourceJunctionVertex: SourceJunctionVertex
            ): RetrievalFunctionSpec.SpecBuilder {
                this.sourceVerticesByPathBuilder.put(
                    sourceJunctionVertex.path,
                    sourceJunctionVertex.left()
                )
                return this
            }

            override fun addSourceVertex(
                sourceLeafVertex: SourceLeafVertex
            ): RetrievalFunctionSpec.SpecBuilder {
                this.sourceVerticesByPathBuilder.put(
                    sourceLeafVertex.path,
                    sourceLeafVertex.right()
                )
                return this
            }

            override fun addParameterVertex(
                parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>
            ): RetrievalFunctionSpec.SpecBuilder {
                this.parameterVerticesByPathBuilder.put(
                    parameterJunctionOrLeafVertex.fold(
                        ParameterJunctionVertex::path,
                        ParameterLeafVertex::path
                    ),
                    parameterJunctionOrLeafVertex
                )
                return this
            }

            override fun addParameterVertex(
                parameterJunctionVertex: ParameterJunctionVertex
            ): RetrievalFunctionSpec.SpecBuilder {
                this.parameterVerticesByPathBuilder.put(
                    parameterJunctionVertex.path,
                    parameterJunctionVertex.left()
                )
                return this
            }

            override fun addParameterVertex(
                parameterLeafVertex: ParameterLeafVertex
            ): RetrievalFunctionSpec.SpecBuilder {
                parameterVerticesByPathBuilder.put(
                    parameterLeafVertex.path,
                    parameterLeafVertex.right()
                )
                return this
            }

            override fun build(): RetrievalFunctionSpec {
                // TODO: Add check that if data_source has changed, whether the vertices
                // support the current data_source is reassessed
                return when {
                    sourceVerticesByPathBuilder.any { (_, sjvOrSlv) ->
                        !sjvOrSlv
                            .fold(
                                SourceJunctionVertex::compositeAttribute,
                                SourceLeafVertex::compositeAttribute
                            )
                            .getSourceAttributeByDataSource()
                            .containsKey(dataSource.key)
                    } -> {
                        val sourceJunctionOrLeafVertexPathsWithoutDatasourceRep =
                            sourceVerticesByPathBuilder
                                .asSequence()
                                .filter { (_, sjvOrSlv) ->
                                    !sjvOrSlv
                                        .fold(
                                            SourceJunctionVertex::compositeAttribute,
                                            SourceLeafVertex::compositeAttribute
                                        )
                                        .getSourceAttributeByDataSource()
                                        .containsKey(dataSource.key)
                                }
                                .map { (p, _) -> p }
                                .joinToString(", ", "{ ", " }")
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """source_junction_or_leaf_vertex (-ies) does 
                                |not have a representation 
                                |in the specified datasource for this spec: 
                                |[ datasource.key.name: ${dataSource.key.name}, 
                                |source_junction_or_leaf_vertex(-ies).path: 
                                |${sourceJunctionOrLeafVertexPathsWithoutDatasourceRep}  
                                |]""".flatten()
                        )
                    }
                    parameterVerticesByPathBuilder.any { (_, pjvOrPlv) ->
                        !pjvOrPlv
                            .fold(
                                ParameterJunctionVertex::compositeParameterAttribute,
                                ParameterLeafVertex::compositeParameterAttribute
                            )
                            .getParameterAttributesByDataSource()
                            .containsKey(dataSource.key)
                    } -> {
                        val parameterJunctionOrLeafVertexPathsWithoutDatasourceRep =
                            parameterVerticesByPathBuilder
                                .asSequence()
                                .filter { (_, pjvOrPlv) ->
                                    !pjvOrPlv
                                        .fold(
                                            ParameterJunctionVertex::compositeParameterAttribute,
                                            ParameterLeafVertex::compositeParameterAttribute
                                        )
                                        .getParameterAttributesByDataSource()
                                        .containsKey(dataSource.key)
                                }
                                .map { (p, _) -> p }
                                .joinToString(", ", "{ ", " }")
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """parameter_junction_or_leaf_vertex (-ies) does 
                                |not have a representation 
                                |in the specified datasource for this spec: 
                                |[ datasource.key.name: ${dataSource.key.name}, 
                                |parameter_junction_or_leaf_vertex(-ies).path: 
                                |${parameterJunctionOrLeafVertexPathsWithoutDatasourceRep}  
                                |]""".flatten()
                        )
                    }
                    else -> {
                        DefaultRetrievalFunctionSpec(
                            dataSource,
                            sourceVerticesByPathBuilder.build(),
                            parameterVerticesByPathBuilder.build()
                        )
                    }
                }
            }
        }
    }

    override fun updateSpec(
        transformer: RetrievalFunctionSpec.SpecBuilder.() -> RetrievalFunctionSpec.SpecBuilder
    ): RetrievalFunctionSpec {
        return transformer
            .invoke(
                DefaultSpecBuilder(
                    dataSource,
                    sourceVerticesByPath.builder(),
                    parameterVerticesByPath.builder()
                )
            )
            .build()
    }
}
