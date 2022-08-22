package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.toOption
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.service.MaterializationGraphVertexContext.Builder
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.language.Argument
import graphql.language.Field

internal class DefaultMaterializationGraphVertexContextFactory :
    MaterializationGraphVertexContextFactory {

    companion object {

        internal class DefaultBuilder<V : SchematicVertex>(
            private var metamodelGraph: MetamodelGraph,
            private var graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            private val vertex: V,
            private var field: Field? = null,
            private var argument: Argument? = null
        ) : Builder<V> {

            override fun graph(
                graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
            ): Builder<V> {
                this.graph = graph
                return this
            }

            override fun <
                NV : SchematicVertex, SJV : SourceJunctionVertex, SLV : SourceLeafVertex
            > nextSourceVertex(
                nextVertex: Either<SJV, SLV>,
                field: Field,
            ): Builder<NV> {
                val nextVertexUnwrapped: NV =
                    nextVertex.fold(
                        { sjv ->
                            @Suppress("UNCHECKED_CAST") //
                            sjv as NV
                        },
                        { slv ->
                            @Suppress("UNCHECKED_CAST") //
                            slv as NV
                        }
                    )
                return DefaultBuilder<NV>(metamodelGraph, graph, nextVertexUnwrapped, field, null)
            }

            override fun <
                NV : SchematicVertex, SJV : SourceJunctionVertex, SLV : SourceLeafVertex
            > nextSourceVertex(nextVertex: Either<SJV, SLV>): Builder<NV> {
                val nextVertexUnwrapped: NV =
                    nextVertex.fold(
                        { sjv ->
                            @Suppress("UNCHECKED_CAST") //
                            sjv as NV
                        },
                        { slv ->
                            @Suppress("UNCHECKED_CAST") //
                            slv as NV
                        }
                    )
                return DefaultBuilder<NV>(metamodelGraph, graph, nextVertexUnwrapped, null, null)
            }

            override fun <
                NV : SchematicVertex, PJV : ParameterJunctionVertex, PLV : ParameterLeafVertex
            > nextParameterVertex(
                nextVertex: Either<PJV, PLV>,
                argument: Argument,
            ): Builder<NV> {
                val nextVertexUnwrapped: NV =
                    nextVertex.fold(
                        { pjv ->
                            @Suppress("UNCHECKED_CAST") //
                            pjv as NV
                        },
                        { plv ->
                            @Suppress("UNCHECKED_CAST") //
                            plv as NV
                        }
                    )
                return DefaultBuilder<NV>(
                    metamodelGraph,
                    graph,
                    nextVertexUnwrapped,
                    null,
                    argument
                )
            }

            override fun <
                NV : SchematicVertex, PJV : ParameterJunctionVertex, PLV : ParameterLeafVertex
            > nextParameterVertex(nextVertex: Either<PJV, PLV>): Builder<NV> {
                val nextVertexUnwrapped: NV =
                    nextVertex.fold(
                        { pjv ->
                            @Suppress("UNCHECKED_CAST") //
                            pjv as NV
                        },
                        { plv ->
                            @Suppress("UNCHECKED_CAST") //
                            plv as NV
                        }
                    )
                return DefaultBuilder<NV>(
                    metamodelGraph,
                    graph,
                    nextVertexUnwrapped,
                    null,
                    null,
                )
            }

            override fun build(): MaterializationGraphVertexContext<V> {
                @Suppress("UNCHECKED_CAST") //
                return when (
                    SchematicGraphVertexType.getSchematicGraphTypeForVertexSubtype(vertex::class)
                        .orNull()
                ) {
                    null -> {
                        // TODO: Replace with module specific exception type
                        throw IllegalArgumentException(
                            "schematic_vertex does not map to graph_vertex_type: [ type: ${vertex::class.qualifiedName} ]"
                        )
                    }
                    SchematicGraphVertexType.SOURCE_ROOT_VERTEX -> {
                        DefaultSourceRootMaterializationGraphVertexContext(
                            metamodelGraph,
                            graph,
                            vertex as SourceRootVertex
                        )
                    }
                    SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX -> {
                        DefaultSourceJunctionMaterializationGraphVertexContext(
                            metamodelGraph,
                            graph,
                            field.toOption(),
                            vertex as SourceJunctionVertex
                        )
                    }
                    SchematicGraphVertexType.SOURCE_LEAF_VERTEX -> {
                        DefaultSourceLeafMaterializationGraphVertexContext(
                            metamodelGraph,
                            graph,
                            field.toOption(),
                            vertex as SourceLeafVertex
                        )
                    }
                    SchematicGraphVertexType.PARAMETER_JUNCTION_VERTEX -> {
                        DefaultParameterJunctionMaterializationGraphVertexContext(
                            metamodelGraph,
                            graph,
                            argument.toOption(),
                            vertex as ParameterJunctionVertex
                        )
                    }
                    SchematicGraphVertexType.PARAMETER_LEAF_VERTEX -> {
                        DefaultParameterLeafMaterializationGraphVertexContext(
                            metamodelGraph,
                            graph,
                            argument.toOption(),
                            vertex as ParameterLeafVertex
                        )
                    }
                }
                    as MaterializationGraphVertexContext<V>
            }
        }

        internal class DefaultSourceRootMaterializationGraphVertexContext(
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val currentVertex: SourceRootVertex
        ) : SourceRootMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceRootVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(DefaultBuilder<SourceRootVertex>(metamodelGraph, graph, currentVertex))
                    .build()
            }
        }

        internal class DefaultSourceJunctionMaterializationGraphVertexContext(
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val field: Option<Field>,
            override val currentVertex: SourceJunctionVertex
        ) : SourceJunctionMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceJunctionVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<SourceJunctionVertex>(
                            metamodelGraph,
                            graph,
                            currentVertex,
                            field.orNull()
                        )
                    )
                    .build()
            }
        }

        internal class DefaultSourceLeafMaterializationGraphVertexContext(
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val field: Option<Field>,
            override val currentVertex: SourceLeafVertex
        ) : SourceLeafMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceLeafVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<SourceLeafVertex>(
                            metamodelGraph,
                            graph,
                            currentVertex,
                            field.orNull()
                        )
                    )
                    .build()
            }
        }

        internal class DefaultParameterJunctionMaterializationGraphVertexContext(
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val argument: Option<Argument>,
            override val currentVertex: ParameterJunctionVertex
        ) : ParameterJunctionMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<ParameterJunctionVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<ParameterJunctionVertex>(
                            metamodelGraph,
                            graph,
                            currentVertex,
                            null,
                            argument.orNull()
                        )
                    )
                    .build()
            }
        }

        internal class DefaultParameterLeafMaterializationGraphVertexContext(
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val argument: Option<Argument>,
            override val currentVertex: ParameterLeafVertex
        ) : ParameterLeafMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<ParameterLeafVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<ParameterLeafVertex>(
                            metamodelGraph,
                            graph,
                            currentVertex,
                            null,
                            argument.orNull()
                        )
                    )
                    .build()
            }
        }
    }

    override fun createSourceRootVertexContext(
        sourceRootVertex: SourceRootVertex,
        metamodelGraph: MetamodelGraph
    ): SourceRootMaterializationGraphVertexContext {
        return DefaultSourceRootMaterializationGraphVertexContext(
            metamodelGraph,
            PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            sourceRootVertex
        )
    }
}
