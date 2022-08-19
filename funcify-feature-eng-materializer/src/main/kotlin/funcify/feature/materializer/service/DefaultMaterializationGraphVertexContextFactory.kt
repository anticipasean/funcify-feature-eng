package funcify.feature.materializer.service

import arrow.core.Either
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.service.MaterializationGraphVertexContext.Builder
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
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
            private val session: SingleRequestFieldMaterializationSession,
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
                return DefaultBuilder<NV>(session, graph, nextVertexUnwrapped, field, null)
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
                return DefaultBuilder<NV>(session, graph, nextVertexUnwrapped, null, argument)
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
                            session,
                            graph,
                            vertex as SourceRootVertex
                        )
                    }
                    SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX -> {
                        DefaultSourceJunctionMaterializationGraphVertexContext(
                            session,
                            graph,
                            field!!,
                            vertex as SourceJunctionVertex
                        )
                    }
                    SchematicGraphVertexType.SOURCE_LEAF_VERTEX -> {
                        DefaultSourceLeafMaterializationGraphVertexContext(
                            session,
                            graph,
                            field!!,
                            vertex as SourceLeafVertex
                        )
                    }
                    SchematicGraphVertexType.PARAMETER_JUNCTION_VERTEX -> {
                        DefaultParameterJunctionMaterializationGraphVertexContext(
                            session,
                            graph,
                            argument!!,
                            vertex as ParameterJunctionVertex
                        )
                    }
                    SchematicGraphVertexType.PARAMETER_LEAF_VERTEX -> {
                        DefaultParameterLeafMaterializationGraphVertexContext(
                            session,
                            graph,
                            argument!!,
                            vertex as ParameterLeafVertex
                        )
                    }
                }
                    as MaterializationGraphVertexContext<V>
            }
        }

        internal class DefaultSourceRootMaterializationGraphVertexContext(
            override val session: SingleRequestFieldMaterializationSession,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val currentVertex: SourceRootVertex
        ) : SourceRootMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceRootVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(DefaultBuilder<SourceRootVertex>(session, graph, currentVertex))
                    .build()
            }
        }

        internal class DefaultSourceJunctionMaterializationGraphVertexContext(
            override val session: SingleRequestFieldMaterializationSession,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val field: Field,
            override val currentVertex: SourceJunctionVertex
        ) : SourceJunctionMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceJunctionVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<SourceJunctionVertex>(session, graph, currentVertex, field)
                    )
                    .build()
            }
        }

        internal class DefaultSourceLeafMaterializationGraphVertexContext(
            override val session: SingleRequestFieldMaterializationSession,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val field: Field,
            override val currentVertex: SourceLeafVertex
        ) : SourceLeafMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<SourceLeafVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(DefaultBuilder<SourceLeafVertex>(session, graph, currentVertex, field))
                    .build()
            }
        }

        internal class DefaultParameterJunctionMaterializationGraphVertexContext(
            override val session: SingleRequestFieldMaterializationSession,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val argument: Argument,
            override val currentVertex: ParameterJunctionVertex
        ) : ParameterJunctionMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<ParameterJunctionVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<ParameterJunctionVertex>(
                            session,
                            graph,
                            currentVertex,
                            null,
                            argument
                        )
                    )
                    .build()
            }
        }

        internal class DefaultParameterLeafMaterializationGraphVertexContext(
            override val session: SingleRequestFieldMaterializationSession,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val argument: Argument,
            override val currentVertex: ParameterLeafVertex
        ) : ParameterLeafMaterializationGraphVertexContext {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<ParameterLeafVertex>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(
                        DefaultBuilder<ParameterLeafVertex>(
                            session,
                            graph,
                            currentVertex,
                            null,
                            argument
                        )
                    )
                    .build()
            }
        }
    }

    override fun createSourceRootVertexContextInSession(
        sourceRootVertex: SourceRootVertex,
        singleRequestFieldMaterializationSession: SingleRequestFieldMaterializationSession,
    ): SourceRootMaterializationGraphVertexContext {
        return DefaultSourceRootMaterializationGraphVertexContext(
            singleRequestFieldMaterializationSession,
            PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            sourceRootVertex
        )
    }
}
