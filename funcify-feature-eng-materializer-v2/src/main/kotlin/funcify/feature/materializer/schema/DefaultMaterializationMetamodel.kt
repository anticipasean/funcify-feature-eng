package funcify.feature.materializer.schema

import funcify.feature.schema.Metamodel
import graphql.schema.GraphQLSchema
import java.time.Instant

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val metamodel: Metamodel,
    override val materializationGraphQLSchema: GraphQLSchema
) : MaterializationMetamodel {}
