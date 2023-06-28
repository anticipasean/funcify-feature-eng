package funcify.feature.schema.datasource

import kotlin.reflect.KClass

interface DataElementSource<SI : SourceIndex<SI>> {

    val name: String

    val sourceType: SourceType

    val key: Key<SI>

    val sourceMetamodel: SourceMetamodel<SI>

    /**
     * [Key]s reference the datasource on which they are a property and are intended to be used in
     * place of the [DataElementSource] instance itself in maps since [DataElementSource] instances may contain
     * values with inconsistent hash codes, thereby rendering them unfit for use as map keys
     */
    interface Key<SI : SourceIndex<SI>> {

        val sourceIndexType: KClass<SI>

        val name: String

        val sourceType: SourceType
    }
}
