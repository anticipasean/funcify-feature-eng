package funcify.feature.schema.strategy

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.factory.SchematicVertexFactory
import funcify.feature.schema.path.SchematicPath

data class DefaultSchematicVertexRemappingContext<SI : SourceIndex<SI>>(
    override val schematicVertexFactory: SchematicVertexFactory,
    override val schematicVertexPath: SchematicPath,
    override val sourcePath: SchematicPath,
    override val dataSourceKey: DataSource.Key<SI>,
    override val dataSourceMetamodel: SourceMetamodel<SI>,
    override val existingMetamodelGraph: MetamodelGraph
) : SchematicVertexRemappingContext<SI> {

    companion object {
        internal class DefaultBuilder<SI : SourceIndex<SI>> :
            SchematicVertexRemappingContext.Builder<SI> {

            override fun addOrUpdateVertex(
                schematicVertex: SchematicVertex
            ): SchematicVertexRemappingContext.Builder<SI> {
                TODO("Not yet implemented")
            }

            override fun <NSI : SourceIndex<NSI>> nextSourceIndex(
                schematicVertexPath: SchematicPath,
                sourceIndex: SourceIndex<NSI>,
                dataSourceKey: DataSource.Key<NSI>,
                dataSourceMetamodel: SourceMetamodel<NSI>,
                metamodelGraph: MetamodelGraph,
            ): SchematicVertexRemappingContext.Builder<NSI> {
                TODO("Not yet implemented")
            }

            override fun build(): SchematicVertexRemappingContext<SI> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun <NSI : SourceIndex<NSI>> update(
        transformer:
            SchematicVertexRemappingContext.Builder<
                SI>.() -> SchematicVertexRemappingContext.Builder<NSI>
    ): SchematicVertexRemappingContext<NSI> {
        TODO("Not yet implemented")
    }
}
