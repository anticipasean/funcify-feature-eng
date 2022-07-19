package funcify.feature.datasource.graphql.metadata.filter

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLOutputType
import org.slf4j.Logger
import org.springframework.stereotype.Component

@Component
class InternalServiceTypesExcludingSourceMetadataFilter : GraphQLApiSourceMetadataFilter {

    companion object {
        private val logger: Logger = loggerFor<InternalServiceTypesExcludingSourceMetadataFilter>()
    }

    override fun includeGraphQLFieldDefinition(
        graphQLFieldDefinition: GraphQLFieldDefinition
    ): Boolean {
        return fieldNameInPublicFormat(graphQLFieldDefinition) &&
            outputTypeNameInPublicFormat(graphQLFieldDefinition)
    }

    private fun fieldNameInPublicFormat(graphQLFieldDefinition: GraphQLFieldDefinition): Boolean {
        return !graphQLFieldDefinition.name.startsWith("_")
    }

    private fun outputTypeNameInPublicFormat(
        graphQLFieldDefinition: GraphQLFieldDefinition
    ): Boolean {
        return when (val outputType: GraphQLOutputType = graphQLFieldDefinition.type) {
            is GraphQLNamedOutputType -> !outputType.name.startsWith("_")
            else -> true
        }
    }
}
