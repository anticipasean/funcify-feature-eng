package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath

interface SchematicVertexFactory {

    fun createVertexForPath(schematicPath: SchematicPath): SourceIndexSpec

    interface SourceIndexSpec {

        fun <SI : SourceIndex> forSourceIndex(sourceIndex: SI): DataSourceSpec<SI> {
            return when (sourceIndex) {
                is SourceContainerType<*> -> forSourceContainerType(sourceIndex)
                is SourceAttribute -> forSourceAttribute(sourceIndex)
                else ->
                    throw SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun <SI : SourceIndex> forSourceAttribute(
            sourceAttribute: SourceAttribute
        ): DataSourceSpec<SI>

        fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
            sourceContainerType: SourceContainerType<A>
        ): DataSourceSpec<SI>

        fun fromExistingVertex(
            existingSchematicVertex: SchematicVertex
        ): ExistingSchematicVertexSpec
    }

    interface ExistingSchematicVertexSpec {

        fun <SI : SourceIndex> forSourceIndex(sourceIndex: SI): DataSourceSpec<SI> {
            return when (sourceIndex) {
                is SourceContainerType<*> -> forSourceContainerType(sourceIndex)
                is SourceAttribute -> forSourceAttribute(sourceIndex)
                else ->
                    throw SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun <SI : SourceIndex> forSourceAttribute(
            sourceAttribute: SourceAttribute
        ): DataSourceSpec<SI>

        fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
            sourceContainerType: SourceContainerType<A>
        ): DataSourceSpec<SI>
    }

    interface DataSourceSpec<SI : SourceIndex> {
        fun onDataSource(dataSource: DataSource<SI>): SchematicVertex
    }
}
