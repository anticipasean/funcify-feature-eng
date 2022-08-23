package funcify.feature.materializer.schema

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.RequestParameterEdge.Builder
import funcify.feature.materializer.schema.RequestParameterEdge.DependentValueRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.MaterializedValueRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge.SpecBuilder
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultRequestParameterEdgeFactory : RequestParameterEdgeFactory {

    companion object {

        internal data class DefaultMaterializedValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val materializedJsonValue: JsonNode
        ) : MaterializedValueRequestParameterEdge {}

        internal data class DefaultRetrievalFunctionSpecRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val dataSource: DataSource<*>,
            override val sourceVerticesByPath:
                PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentMapOf(),
            override val parameterVerticesByPath:
                PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentMapOf(),
        ) : RetrievalFunctionSpecRequestParameterEdge {

            companion object {
                private class DefaultSpecBuilder(
                    private val id: Pair<SchematicPath, SchematicPath>,
                    private var dataSource: DataSource<*>,
                    private val sourceVerticesByPathBuilder:
                        PersistentMap.Builder<
                            SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>,
                    private val parameterVerticesByPathBuilder:
                        PersistentMap.Builder<
                            SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>
                ) : SpecBuilder {

                    override fun dataSource(dataSource: DataSource<*>): SpecBuilder {
                        this.dataSource = dataSource
                        return this
                    }

                    override fun addSourceVertex(
                        sourceJunctionVertex: SourceJunctionVertex
                    ): SpecBuilder {
                        this.sourceVerticesByPathBuilder.put(
                            sourceJunctionVertex.path,
                            sourceJunctionVertex.left()
                        )
                        return this
                    }

                    override fun addSourceVertex(sourceLeafVertex: SourceLeafVertex): SpecBuilder {
                        this.sourceVerticesByPathBuilder.put(
                            sourceLeafVertex.path,
                            sourceLeafVertex.right()
                        )
                        return this
                    }

                    override fun addParameterVertex(
                        parameterJunctionVertex: ParameterJunctionVertex
                    ): SpecBuilder {
                        this.parameterVerticesByPathBuilder.put(
                            parameterJunctionVertex.path,
                            parameterJunctionVertex.left()
                        )
                        return this
                    }

                    override fun addParameterVertex(
                        parameterLeafVertex: ParameterLeafVertex
                    ): SpecBuilder {
                        parameterVerticesByPathBuilder.put(
                            parameterLeafVertex.path,
                            parameterLeafVertex.right()
                        )
                        return this
                    }

                    override fun build(): RetrievalFunctionSpecRequestParameterEdge {
                        // TODO: Add check that if data_source has changed, whether the vertices
                        // support the current data_source is reassessed
                        return DefaultRetrievalFunctionSpecRequestParameterEdge(
                            id,
                            dataSource,
                            sourceVerticesByPathBuilder.build(),
                            parameterVerticesByPathBuilder.build()
                        )
                    }
                }
            }

            override fun updateSpec(
                transformer: SpecBuilder.() -> SpecBuilder
            ): RetrievalFunctionSpecRequestParameterEdge {
                return transformer
                    .invoke(
                        DefaultSpecBuilder(
                            id,
                            dataSource,
                            sourceVerticesByPath.builder(),
                            parameterVerticesByPath.builder()
                        )
                    )
                    .build()
            }
        }

        internal data class DefaultDependentValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val extractionFunction:
                (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
        ) : DependentValueRequestParameterEdge {}

        internal data class DefaultBuilder(
            var pathPair: Pair<SchematicPath, SchematicPath>? = null,
            var materializedJsonNode: JsonNode? = null,
            var dataSource: DataSource<*>? = null,
            var specBuilderFunction: (SpecBuilder.() -> SpecBuilder)? = null,
            var extractor: ((ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>)? = null
        ) : Builder {

            override fun fromPathToPath(path1: SchematicPath, path2: SchematicPath): Builder {
                this.pathPair = path1 to path2
                return this
            }

            override fun materializedValue(materializedJsonNode: JsonNode): Builder {
                this.materializedJsonNode = materializedJsonNode
                return this
            }

            override fun retrievalFunctionSpecForDataSource(
                dataSource: DataSource<*>,
                specCreator: SpecBuilder.() -> SpecBuilder,
            ): Builder {
                this.dataSource = dataSource
                this.specBuilderFunction = specCreator
                return this
            }

            override fun extractionFromAncestorFunction(
                extractor: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
            ): Builder {
                this.extractor = extractor
                return this
            }

            override fun build(): RequestParameterEdge {
                return when {
                    pathPair == null -> {
                        throw IllegalArgumentException(
                            "path_pair: [ from path to path ] must be provided for edge creation"
                        )
                    }
                    materializedJsonNode != null -> {
                        DefaultMaterializedValueRequestParameterEdge(
                            pathPair!!,
                            materializedJsonNode!!
                        )
                    }
                    dataSource != null && specBuilderFunction != null -> {
                        DefaultRetrievalFunctionSpecRequestParameterEdge(pathPair!!, dataSource!!)
                            .updateSpec(specBuilderFunction!!)
                    }
                    extractor != null -> {
                        DefaultDependentValueRequestParameterEdge(pathPair!!, extractor!!)
                    }
                    else -> {
                        // TODO: Complete list of edge parameter types when ready and convert to
                        // module exception typ
                        throw IllegalArgumentException(
                            "one or more of the following arguments is missing for creation of a request_parameter_edge: ["
                        )
                    }
                }
            }
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder()
    }
}
