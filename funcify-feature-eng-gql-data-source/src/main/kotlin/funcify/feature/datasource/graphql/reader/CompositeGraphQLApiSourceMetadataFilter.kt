package funcify.feature.datasource.graphql.reader

import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.persistentListOf
import org.springframework.beans.factory.ObjectProvider

class CompositeGraphQLApiSourceMetadataFilter(
    filtersProvider: ObjectProvider<GraphQLApiSourceMetadataFilter>
) : GraphQLApiSourceMetadataFilter {

    private val filtersList: List<GraphQLApiSourceMetadataFilter> by lazy {
        filtersProvider.fold(persistentListOf()) { pl, f -> pl.add(f) }
    }

    override fun includeGraphQLFieldDefinition(
        graphQLFieldDefinition: GraphQLFieldDefinition
    ): Boolean {
        return filtersList.parallelStream().allMatch { f: GraphQLApiSourceMetadataFilter ->
            f.includeGraphQLFieldDefinition(graphQLFieldDefinition)
        }
    }
}
