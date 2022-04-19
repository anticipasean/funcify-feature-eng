package funcify.feature.schema.datasource

interface DataSource<SI : SourceIndex> {

    val sourceType: DataSourceType

    val sourceMetamodel: SourceMetamodel<SI>
}
