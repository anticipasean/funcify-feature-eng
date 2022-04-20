package funcify.feature.schema.datasource

interface DataSource<SI : SourceIndex> {

    val name: String

    val sourceType: DataSourceType

    val sourceMetamodel: SourceMetamodel<SI>
}
