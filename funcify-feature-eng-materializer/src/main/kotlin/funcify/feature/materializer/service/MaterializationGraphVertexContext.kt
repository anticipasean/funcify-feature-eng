package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.Option
import funcify.feature.materializer.schema.RequestParameterEdge
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
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
sealed interface MaterializationGraphVertexContext<V : SchematicVertex> {

    val graphQLSchema: GraphQLSchema

    val metamodelGraph: MetamodelGraph

    val graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    val currentVertex: V

    val vertexGraphType: SchematicGraphVertexType

    val path: SchematicPath
        get() = currentVertex.path

    val parentPath: Option<SchematicPath>
        get() = currentVertex.path.getParentPath()

    val parentVertex: Option<SchematicVertex>
        get() = currentVertex.path.getParentPath().flatMap { pp -> graph.getVertex(pp) }

    fun <NV : SchematicVertex> update(
        transformer: Builder<V>.() -> Builder<NV>
    ): MaterializationGraphVertexContext<NV>

    interface Builder<V : SchematicVertex> {

        fun graph(
            graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
        ): Builder<V>

        fun <NV, SJV, SLV> nextSourceVertex(
            nextVertex: Either<SJV, SLV>,
            field: Field
        ): Builder<NV> where
        NV : SchematicVertex,
        SJV : SourceJunctionVertex,
        SLV : SourceLeafVertex

        fun <NV, SJV, SLV> nextSourceVertex(nextVertex: Either<SJV, SLV>): Builder<NV> where
        NV : SchematicVertex,
        SJV : SourceJunctionVertex,
        SLV : SourceLeafVertex

        fun <NV, PJV, PLV> nextParameterVertex(
            nextVertex: Either<PJV, PLV>,
            argument: Argument
        ): Builder<NV> where
        NV : SchematicVertex,
        PJV : ParameterJunctionVertex,
        PLV : ParameterLeafVertex

        fun <NV, PJV, PLV> nextParameterVertex(nextVertex: Either<PJV, PLV>): Builder<NV> where
        NV : SchematicVertex,
        PJV : ParameterJunctionVertex,
        PLV : ParameterLeafVertex

        fun build(): MaterializationGraphVertexContext<V>
    }

    interface SourceRootMaterializationGraphVertexContext :
        MaterializationGraphVertexContext<SourceRootVertex> {
        override val currentVertex: SourceRootVertex
        override val vertexGraphType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_ROOT_VERTEX
    }
    interface SourceJunctionMaterializationGraphVertexContext :
        MaterializationGraphVertexContext<SourceJunctionVertex> {
        val field: Option<Field>
        override val currentVertex: SourceJunctionVertex
        override val vertexGraphType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX
    }
    interface SourceLeafMaterializationGraphVertexContext :
        MaterializationGraphVertexContext<SourceLeafVertex> {
        val field: Option<Field>
        override val currentVertex: SourceLeafVertex
        override val vertexGraphType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_LEAF_VERTEX
    }
    interface ParameterJunctionMaterializationGraphVertexContext :
        MaterializationGraphVertexContext<ParameterJunctionVertex> {
        val argument: Option<Argument>
        override val currentVertex: ParameterJunctionVertex
        override val vertexGraphType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.PARAMETER_JUNCTION_VERTEX
    }
    interface ParameterLeafMaterializationGraphVertexContext :
        MaterializationGraphVertexContext<ParameterLeafVertex> {
        val argument: Option<Argument>
        override val currentVertex: ParameterLeafVertex
        override val vertexGraphType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.PARAMETER_LEAF_VERTEX
    }
}