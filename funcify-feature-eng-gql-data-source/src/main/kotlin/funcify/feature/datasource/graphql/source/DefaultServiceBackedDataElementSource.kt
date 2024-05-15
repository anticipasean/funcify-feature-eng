package funcify.feature.datasource.graphql.source

import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.ServiceBackedDataElementSource
import funcify.feature.datasource.graphql.source.callable.ServiceBackedDataElementSourceCallableBuilder
import funcify.feature.schema.dataelement.DataElementCallable
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.PersistentSet

internal class DefaultServiceBackedDataElementSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>,
    override val graphQLApiService: GraphQLApiService
) : ServiceBackedDataElementSource {

    override fun builder(): DataElementCallable.Builder {
        return ServiceBackedDataElementSourceCallableBuilder()
    }

}
