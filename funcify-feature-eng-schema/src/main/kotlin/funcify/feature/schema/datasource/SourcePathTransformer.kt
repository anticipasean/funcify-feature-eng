package funcify.feature.schema.datasource

import funcify.feature.schema.path.SchematicPath

interface SourcePathTransformer {

    fun <SI : SourceIndex<SI>> transformSourcePathToSchematicPathForDataSource(
        sourcePath: SchematicPath,
        dataSource: DataSource<SI>
    ): SchematicPath

}
