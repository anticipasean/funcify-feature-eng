package funcify.feature.schema.strategy

import arrow.core.Option
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.factory.SchematicVertexFactory
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexRemappingContext<SI : SourceIndex<SI>> {

    val schematicVertexFactory: SchematicVertexFactory

    val schematicVertexPath: SchematicPath

    val existingSchematicVertex: Option<SchematicVertex>
        get() = existingMetamodelGraph.getVertex(schematicVertexPath)

    val sourcePath: SchematicPath

    val sourceIndices: ImmutableSet<SI>
        get() = dataSourceMetamodel.sourceIndicesByPath[sourcePath] ?: persistentSetOf()

    val dataSourceKey: DataSource.Key<SI>

    val dataSourceMetamodel: SourceMetamodel<SI>

    val existingMetamodelGraph: MetamodelGraph

    fun <NSI : SourceIndex<NSI>> update(
        transformer: Builder<SI>.() -> Builder<NSI>
    ): SchematicVertexRemappingContext<NSI>

    interface Builder<SI : SourceIndex<SI>> {

        fun addOrUpdateVertex(schematicVertex: SchematicVertex): Builder<SI>

        fun <NSI : SourceIndex<NSI>> nextSourceIndex(
            schematicVertexPath: SchematicPath,
            sourceIndex: SourceIndex<NSI>,
            dataSourceKey: DataSource.Key<NSI>,
            dataSourceMetamodel: SourceMetamodel<NSI>,
            metamodelGraph: MetamodelGraph
        ): Builder<NSI>

        fun build(): SchematicVertexRemappingContext<SI>
    }
}
