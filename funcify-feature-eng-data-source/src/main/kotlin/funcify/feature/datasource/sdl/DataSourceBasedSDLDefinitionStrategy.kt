package funcify.feature.datasource.sdl

import arrow.core.Option
import arrow.core.Predicate
import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-06-29
 */
interface DataSourceBasedSDLDefinitionStrategy<T : Any> : SchematicVertexSDLDefinitionStrategy<T> {

    interface DataSourceAttribute<out T : Any> {
        val name: String
        val valueExtractor: (DataSource.Key<*>) -> T
        val expectedValue: T
    }

    val expectedDataSourceAttributeValues: ImmutableSet<DataSourceAttribute<*>>
    override fun canBeAppliedToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Boolean {
        val schematicGraphTypeOption: Option<SchematicGraphVertexType> =
            SchematicGraphVertexType.getSchematicGraphTypeForVertexSubtype(
                context.currentVertex::class
            )
        /*
         * Early Exit Approach: If vertex cannot be mapped to known graph vertex type,
         * then its data_source_keys cannot be extracted
         */
        if (!schematicGraphTypeOption.isDefined()) {
            return false
        }
        /*
         * Condition: At least one data_source_key in the keys iterable can have its
         * data_source_attribute values extracted and those _all_ match the expected values
         * for those attributes
         *
         * Note: If there aren't any expected attribute values, all data_source_keys
         * will be considered "matching"
         */
        val dataSourceKeysCondition: Predicate<Iterable<DataSource.Key<*>>> = { dataSourceKeys ->
            dataSourceKeys.any { dataSourceKey ->
                expectedDataSourceAttributeValues.asSequence().fold(true) {
                    outcome: Boolean,
                    applicableAttribute: DataSourceAttribute<*> ->
                    outcome &&
                        try {
                            applicableAttribute.valueExtractor.invoke(dataSourceKey) ==
                                applicableAttribute.expectedValue
                        } catch (t: Throwable) {
                            false
                        }
                }
            }
        }
        return when (schematicGraphTypeOption.orNull()!!) {
            SchematicGraphVertexType.SOURCE_ROOT_VERTEX -> {
                context
                    .currentVertex
                    .toOption()
                    .filterIsInstance<SourceRootVertex>()
                    .map { srt ->
                        srt.compositeContainerType.getSourceContainerTypeByDataSource().keys
                    }
                    .filter(dataSourceKeysCondition)
                    .isDefined()
            }
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX -> {
                context
                    .currentVertex
                    .toOption()
                    .filterIsInstance<SourceJunctionVertex>()
                    .map { sjt ->
                        sjt.compositeContainerType.getSourceContainerTypeByDataSource().keys
                    }
                    .filter(dataSourceKeysCondition)
                    .isDefined()
            }
            SchematicGraphVertexType.SOURCE_LEAF_VERTEX -> {
                context
                    .currentVertex
                    .toOption()
                    .filterIsInstance<SourceLeafVertex>()
                    .map { sjt -> sjt.compositeAttribute.getSourceAttributeByDataSource().keys }
                    .filter(dataSourceKeysCondition)
                    .isDefined()
            }
            SchematicGraphVertexType.PARAMETER_JUNCTION_VERTEX -> {
                context
                    .currentVertex
                    .toOption()
                    .filterIsInstance<ParameterJunctionVertex>()
                    .map { sjt ->
                        sjt.compositeParameterContainerType.getParameterContainerTypeByDataSource()
                            .keys
                    }
                    .filter(dataSourceKeysCondition)
                    .isDefined()
            }
            SchematicGraphVertexType.PARAMETER_LEAF_VERTEX -> {
                context
                    .currentVertex
                    .toOption()
                    .filterIsInstance<ParameterLeafVertex>()
                    .map { sjt ->
                        sjt.compositeParameterAttribute.getParameterAttributesByDataSource().keys
                    }
                    .filter(dataSourceKeysCondition)
                    .isDefined()
            }
        }
    }
}
