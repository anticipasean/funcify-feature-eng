package funcify.feature.materializer.schema

import funcify.feature.schema.MetamodelGraph
import graphql.schema.GraphQLSchema
import java.time.Instant

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val metamodelGraph: MetamodelGraph,
    override val materializationGraphQLSchema: GraphQLSchema
) : MaterializationMetamodel {}
