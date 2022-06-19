package funcify.feature.schema.factory

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try

interface SchematicVertexFactory {

    fun createVertexForPath(schematicPath: SchematicPath): SourceIndexSpec

    interface SourceIndexSpec {

        fun <SI : SourceIndex<SI>> forSourceIndex(sourceIndex: SI): DataSourceSpec<SI> {
            return when (sourceIndex) {
                is SourceContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceContainerType(sourceIndex as SourceContainerType<SI, *>)
                }
                is SourceAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceAttribute(sourceIndex as SourceAttribute<SI>)
                }
                else ->
                    throw SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun <SI : SourceIndex<SI>> forSourceAttribute(
            sourceAttribute: SourceAttribute<SI>
        ): DataSourceSpec<SI>

        fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
            sourceContainerType: SourceContainerType<SI, A>
        ): DataSourceSpec<SI>

        fun fromExistingVertex(
            existingSchematicVertex: SchematicVertex
        ): ExistingSchematicVertexSpec
    }

    interface ExistingSchematicVertexSpec {

        fun <SI : SourceIndex<SI>> forSourceIndex(sourceIndex: SI): DataSourceSpec<SI> {
            return when (sourceIndex) {
                is SourceContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceContainerType(sourceIndex as SourceContainerType<SI, *>)
                }
                is SourceAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceAttribute(sourceIndex as SourceAttribute<SI>)
                }
                else ->
                    throw SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun <SI : SourceIndex<SI>> forSourceAttribute(
            sourceAttribute: SourceAttribute<SI>
        ): DataSourceSpec<SI>

        fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
            sourceContainerType: SourceContainerType<SI, A>
        ): DataSourceSpec<SI>
    }

    interface DataSourceSpec<SI : SourceIndex<SI>> {
        fun onDataSource(dataSource: DataSource<SI>): Try<SchematicVertex>
    }
}
