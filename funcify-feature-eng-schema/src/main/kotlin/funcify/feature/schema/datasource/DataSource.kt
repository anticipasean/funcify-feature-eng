package funcify.feature.schema.datasource

interface DataSource<SI : SourceIndex> {

    val name: String

    val sourceType: DataSourceType

    val key: Key<SI>

    val sourceMetamodel: SourceMetamodel<SI>

    /**
     * [Key]s reference the datasource on which they are a property and are intended to be used in
     * place of the [DataSource] instance itself in maps since [DataSource] instances may contain
     * values with inconsistent hash codes, thereby rendering them unfit for use as map keys
     */
    interface Key<SI : SourceIndex> {

        val name: String

        val sourceType: DataSourceType

    }
}
