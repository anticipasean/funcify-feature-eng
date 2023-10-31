package funcify.feature.datasource.graphql.extract

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-10-29
 */
interface JsonNodeGraphQLDomainSelector {

    fun selectGraphQLDomainWithJsonNode(
        domainPath: GQLOperationPath,
        domainGraphQLFieldDefinition: GraphQLFieldDefinition,
        selections: ImmutableSet<GQLOperationPath>,
        jsonNode: JsonNode
    ): JsonNode
}
