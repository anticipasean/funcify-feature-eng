package funcify.feature.materializer.context.graph

import arrow.typeclasses.Semigroup
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.GQLOperationPath
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

    val requestParameterGraph: PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: ImmutableMap<GQLOperationPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<GQLOperationPath, RetrievalFunctionSpec>

    fun update(transformer: Builder.() -> Builder): MaterializationGraphContext

    interface Builder {

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): Builder

        fun operationDefinition(operationDefinition: OperationDefinition): Builder

        fun queryVariables(queryVariables: PersistentMap<String, Any?>): Builder

        fun requestParameterGraph(
            requestParameterGraph:
                PathBasedGraph<GQLOperationPath, SchematicVertex, RequestParameterEdge>
        ): Builder

        fun addVertexToRequestParameterGraph(vertex: SchematicVertex): Builder

        fun addEdgeToRequestParameterGraph(edge: RequestParameterEdge): Builder

        fun removeEdgesFromRequestParameterGraph(edgeId: Pair<GQLOperationPath, GQLOperationPath>): Builder

        fun removeEdgesFromRequestParameterGraph(path1: GQLOperationPath, path2: GQLOperationPath): Builder

        fun materializedParameterValuesByPath(
            materializedParameterValuesByPath: PersistentMap<GQLOperationPath, JsonNode>
        ): Builder

        fun addMaterializedParameterValueForPath(path: GQLOperationPath, value: JsonNode): Builder

        fun parameterIndexPathsBySourceIndexPath(
            parameterIndexPathsBySourceIndexPath:
                PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>
        ): Builder

        fun addParameterIndexPathForSourceIndexPath(
            path: GQLOperationPath,
            parameterIndexPath: GQLOperationPath
        ): Builder

        fun retrievalFunctionSpecsByTopSourceIndexPath(
            retrievalFunctionSpecsByTopSourceIndexPath:
                PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
        ): Builder

        fun addRetrievalFunctionSpecForTopSourceIndexPath(
            path: GQLOperationPath,
            spec: RetrievalFunctionSpec
        ): Builder

        fun build(): MaterializationGraphContext
    }
}
