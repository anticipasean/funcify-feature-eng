package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface MetamodelGraph {

    val dataSourcesByName: ImmutableMap<String, DataSource<*>>

    val schematicVerticesByPath: ImmutableMap<SchematicPath, SchematicVertex>

    interface Builder {

        fun <SI : SourceIndex> addDataSource(dataSource: DataSource<SI>): Builder

        fun build(): Try<MetamodelGraph>
    }
}
