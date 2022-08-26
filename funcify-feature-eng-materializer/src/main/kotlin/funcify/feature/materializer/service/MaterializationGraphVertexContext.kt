package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.language.Argument
import graphql.language.Field
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
sealed interface MaterializationGraphVertexContext<V : SchematicVertex> {

    val graphQLSchema: GraphQLSchema

    val metamodelGraph: MetamodelGraph

    val requestParameterGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>

    val currentVertex: V

    val field: Option<Field>

    val argument: Option<Argument>

    val path: SchematicPath
        get() = currentVertex.path

    val parentPath: Option<SchematicPath>
        get() = currentVertex.path.getParentPath()

    val parentVertex: Option<SchematicVertex>
        get() {
            return currentVertex.path.getParentPath().flatMap { pp ->
                metamodelGraph.pathBasedGraph.getVertex(pp)
            }
        }

    fun <NV : SchematicVertex> update(
        transformer: Builder<V>.() -> Builder<NV>
    ): MaterializationGraphVertexContext<NV>

    interface Builder<V : SchematicVertex> {

        fun addRequestParameterEdge(requestParameterEdge: RequestParameterEdge): Builder<V>

        fun <SJV, SLV> addRetrievalFunctionSpecFor(
            sourceVertex: Either<SJV, SLV>,
            dataSource: DataSource<*>
        ): Builder<V> where SJV : SourceJunctionVertex, SLV : SourceLeafVertex

        fun addRetrievalFunctionSpecFor(
            sourceJunctionVertex: SourceJunctionVertex,
            dataSource: DataSource<*>
        ): Builder<V>

        fun addRetrievalFunctionSpecFor(
            sourceLeafVertex: SourceLeafVertex,
            dataSource: DataSource<*>
        ): Builder<V>

        fun <NV : SchematicVertex> nextVertex(nextVertex: NV): Builder<NV>

        fun <NV : SchematicVertex> nextVertex(nextVertex: NV, field: Field): Builder<NV>

        fun <NV : SchematicVertex> nextVertex(nextVertex: NV, argument: Argument): Builder<NV>

        fun build(): MaterializationGraphVertexContext<V>
    }

    interface RetrievalFunctionSpec {
        val dataSource: DataSource<*>
        val sourceVerticesByPath:
            PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>
        val parameterVerticesByPath:
            PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>

        fun updateSpec(transformer: SpecBuilder.() -> SpecBuilder): RetrievalFunctionSpec

        interface SpecBuilder {
            fun dataSource(dataSource: DataSource<*>): SpecBuilder
            fun addSourceVertex(sourceJunctionVertex: SourceJunctionVertex): SpecBuilder
            fun addSourceVertex(sourceLeafVertex: SourceLeafVertex): SpecBuilder
            fun addParameterVertex(parameterJunctionVertex: ParameterJunctionVertex): SpecBuilder
            fun addParameterVertex(parameterLeafVertex: ParameterLeafVertex): SpecBuilder
            fun build(): RetrievalFunctionSpec
        }
    }
}
