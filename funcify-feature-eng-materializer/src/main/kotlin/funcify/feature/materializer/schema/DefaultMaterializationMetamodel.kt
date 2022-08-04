package funcify.feature.materializer.schema

import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema

internal data class DefaultMaterializationMetamodel(
    override val metamodelGraph: MetamodelGraph,
    override val materializationGraphQLSchema: GraphQLSchema
) : MaterializationMetamodel {}
