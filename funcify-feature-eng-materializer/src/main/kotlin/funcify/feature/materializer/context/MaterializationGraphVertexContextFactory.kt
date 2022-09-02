package funcify.feature.materializer.context

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.vertex.SourceRootVertex
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
interface MaterializationGraphVertexContextFactory {

    fun createSourceRootVertexContext(
        sourceRootVertex: SourceRootVertex,
        metamodelGraph: MetamodelGraph,
        materializationSchema: GraphQLSchema,
        operationDefinition: OperationDefinition,
        queryVariables: Map<String, Any>
    ): MaterializationGraphVertexContext<SourceRootVertex>
}
