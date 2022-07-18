package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy.DataSourceAttribute
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
abstract class DataSourceIndexTypeBasedSDLDefinitionStrategy<SI : SourceIndex<SI>, T : Any>(
    val applicableSourceIndexType: KClass<out SI>
) : DataSourceBasedSDLDefinitionStrategy<T> {

    override val expectedDataSourceAttributeValues: ImmutableSet<DataSourceAttribute<*>> =
        persistentSetOf(
            DataSourceBasedSDLDefinitionStrategy.sourceIndexTypeAttribute(applicableSourceIndexType)
        )
}
