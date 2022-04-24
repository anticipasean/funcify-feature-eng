package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.graph.PathBasedGraph

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
interface MetamodelGraph : PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> {
    interface Builder {

        fun <SI : SourceIndex> addDataSource(dataSource: DataSource<SI>): Builder

        fun build(): Try<MetamodelGraph>
    }
}
