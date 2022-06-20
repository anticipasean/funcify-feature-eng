package funcify.feature.schema.factory

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.path.SchematicPath

/**
 * Default source path transformer takes the name of the data source, converts it into a
 * "snake_case" form, and prepends it to the source path
 */
internal class DefaultSourcePathTransformer : SourcePathTransformer {
    override fun <SI : SourceIndex<SI>> transformSourcePathToSchematicPathForDataSource(
        sourcePath: SchematicPath,
        dataSource: DataSource<SI>
    ): SchematicPath {
        return sourcePath
    }
}
