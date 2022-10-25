package funcify.feature.materializer.context.graph

import arrow.typeclasses.Semigroup
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-10-08
 */
interface MaterializationGraphContext {

    companion object {
        @JvmStatic
        fun semigroup(): Semigroup<MaterializationGraphContext> {
            return MaterializationGraphContextSemigroup
        }
    }

    val materializationMetamodel: MaterializationMetamodel

    val graphQLSchema: GraphQLSchema
        get() = materializationMetamodel.materializationGraphQLSchema

    val metamodelGraph: MetamodelGraph
        get() = materializationMetamodel.metamodelGraph

    val operationDefinition: OperationDefinition

    val queryVariables: ImmutableMap<String, Any?>

    val requestParameterGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: ImmutableMap<SchematicPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>

    fun update(transformer: Builder.() -> Builder): MaterializationGraphContext

    interface Builder {

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): Builder

        fun operationDefinition(operationDefinition: OperationDefinition): Builder

        fun queryVariables(queryVariables: PersistentMap<String, Any?>): Builder

        fun requestParameterGraph(
            requestParameterGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
        ): Builder

        fun addVertexToRequestParameterGraph(vertex: SchematicVertex): Builder

        fun addEdgeToRequestParameterGraph(edge: RequestParameterEdge): Builder

        fun removeEdgesFromRequestParameterGraph(edgeId: Pair<SchematicPath, SchematicPath>): Builder

        fun removeEdgesFromRequestParameterGraph(path1: SchematicPath, path2: SchematicPath): Builder

        fun materializedParameterValuesByPath(
            materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
        ): Builder

        fun addMaterializedParameterValueForPath(path: SchematicPath, value: JsonNode): Builder

        fun parameterIndexPathsBySourceIndexPath(
            parameterIndexPathsBySourceIndexPath:
                PersistentMap<SchematicPath, PersistentSet<SchematicPath>>
        ): Builder

        fun addParameterIndexPathForSourceIndexPath(
            path: SchematicPath,
            parameterIndexPath: SchematicPath
        ): Builder

        fun retrievalFunctionSpecsByTopSourceIndexPath(
            retrievalFunctionSpecsByTopSourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec>
        ): Builder

        fun addRetrievalFunctionSpecForTopSourceIndexPath(
            path: SchematicPath,
            spec: RetrievalFunctionSpec
        ): Builder

        fun build(): MaterializationGraphContext
    }
}
