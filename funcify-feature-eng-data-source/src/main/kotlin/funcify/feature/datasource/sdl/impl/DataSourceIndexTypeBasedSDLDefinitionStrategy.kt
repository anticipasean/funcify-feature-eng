package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy.*
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import kotlin.reflect.KClass
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Strategy: Apply strategy if vertex has a backing data source that maps to the applicable
 * [SourceIndex] type
 * @author smccarron
 * @created 2022-07-01
 */
abstract class DataSourceIndexTypeBasedSDLDefinitionStrategy<SI : SourceIndex<SI>>(
    val applicableSourceIndexType: KClass<out SI>
) : DataSourceBasedSDLDefinitionStrategy {

    companion object {
        data class DefaultDataSourceAttribute<out T : Any>(
            override val name: String,
            override val valueExtractor: (DataSource.Key<*>) -> T,
            override val expectedValue: T
        ) : DataSourceAttribute<T>
    }

    override val expectedDataSourceAttributeValues: ImmutableSet<DataSourceAttribute<*>> =
        persistentSetOf(
            DefaultDataSourceAttribute(
                "sourceIndexType",
                DataSource.Key<*>::sourceIndexType,
                applicableSourceIndexType
            )
        )
}
