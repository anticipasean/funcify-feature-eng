package funcify.feature.materializer.newcontext

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

    val materializationMetamodel: MaterializationMetamodel

    val graphQLSchema: GraphQLSchema
        get() = materializationMetamodel.materializationGraphQLSchema

    val metamodelGraph: MetamodelGraph
        get() = materializationMetamodel.metamodelGraph

    val operationDefinition: OperationDefinition

    val queryVariables: PersistentMap<String, Any>

    val requestParameterGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>

    val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>

    val parameterIndexPathsBySourceIndexPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>>

    val retrievalFunctionSpecByTopSourceIndexPath:
        PersistentMap<SchematicPath, RetrievalFunctionSpec>

    interface Builder {

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): Builder

        fun operationDefinition(operationDefinition: OperationDefinition): Builder

        fun queryVariables(queryVariables: PersistentMap<String, Any>): Builder

        fun requestParameterGraph(
            requestParameterGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
        ): Builder

        fun materializedParameterValuesByPath(
            materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
        ): Builder

        fun parameterIndexPathsBySourceIndexPath(
            parameterIndexPathsBySourceIndexPath:
                PersistentMap<SchematicPath, PersistentSet<SchematicPath>>
        ): Builder

        fun retrievalFunctionSpecByTopSourceIndexPath(
            retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec>
        ): Builder

        fun build(): MaterializationGraphContext
    }
}
