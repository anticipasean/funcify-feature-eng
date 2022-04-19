package funcify.feature.schema

import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath

interface SchematicVertexFactory {

    fun createVertexForPath(schematicPath: SchematicPath): SourceIndexSpec

    interface SourceIndexSpec {

        fun forSourceIndex(sourceIndex: SourceIndex): CompositeIndex {
            return when (sourceIndex) {
                is SourceContainerType<*> -> forSourceContainerType(sourceIndex)
                is SourceAttribute -> forSourceAttribute(sourceIndex)
                else ->
                    throw UnsupportedOperationException(
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun forSourceAttribute(sourceAttribute: SourceAttribute): CompositeAttribute

        fun <A : SourceAttribute> forSourceContainerType(
            sourceContainerType: SourceContainerType<A>
        ): CompositeContainerType

        fun fromExistingVertex(
            existingSchematicVertex: SchematicVertex
        ): ExistingSchematicVertexSpec
    }

    interface ExistingSchematicVertexSpec {

        fun forSourceIndex(sourceIndex: SourceIndex): CompositeIndex {
            return when (sourceIndex) {
                is SourceContainerType<*> -> forSourceContainerType(sourceIndex)
                is SourceAttribute -> forSourceAttribute(sourceIndex)
                else ->
                    throw UnsupportedOperationException(
                        "unsupported source index type: ${sourceIndex::class.qualifiedName}"
                    )
            }
        }

        fun forSourceAttribute(sourceAttribute: SourceAttribute): CompositeAttribute

        fun <A : SourceAttribute> forSourceContainerType(
            sourceContainerType: SourceContainerType<A>
        ): CompositeContainerType
    }
}
