package funcify.feature.datasource.graphql.source

import funcify.feature.datasource.graphql.SchemaOnlyDataElementSource
import funcify.feature.datasource.graphql.source.callable.SchemaOnlyDataElementSourceCallableBuilder
import funcify.feature.schema.dataelement.DataElementCallable
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.PersistentSet

internal class DefaultSchemaOnlyDataElementSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
) : SchemaOnlyDataElementSource {

    override fun builder(): DataElementCallable.Builder {
        return SchemaOnlyDataElementSourceCallableBuilder()
    }

}
