package funcify.feature.datasource.graphql

import funcify.feature.schema.SourceType

object GraphQLSourceType : SourceType {
    override val name: String
        get() = "GraphQL"
}
