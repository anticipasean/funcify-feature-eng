package funcify.feature.schema.factory

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try

interface SchematicVertexFactory {

    fun createVertexForPath(schematicPath: SchematicPath): NameSpec

    interface NameSpec {

        fun withName(conventionalName: ConventionalName): SourceIndexSpec

        fun extractingName(): SourceIndexSpec
    }

    interface SourceIndexSpec {

        fun <SI : SourceIndex<SI>> forSourceIndex(sourceIndex: SI): Try<SchematicVertex> {
            return when (sourceIndex) {
                is SourceContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceContainerType(sourceIndex as SourceContainerType<SI, *>)
                }
                is SourceAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceAttribute(sourceIndex as SourceAttribute<SI>)
                }
                is ParameterContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forParameterContainerType(sourceIndex as ParameterContainerType<SI, *>)
                }
                is ParameterAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forParameterAttribute(sourceIndex as ParameterAttribute<SI>)
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
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
            sourceContainerType: SourceContainerType<SI, A>
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>> forParameterAttribute(
            parameterAttribute: ParameterAttribute<SI>
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
            parameterContainerType: ParameterContainerType<SI, A>
        ): Try<SchematicVertex>

        fun fromExistingVertex(
            existingSchematicVertex: SchematicVertex
        ): ExistingSchematicVertexSpec
    }

    interface ExistingSchematicVertexSpec {

        fun <SI : SourceIndex<SI>> forSourceIndex(sourceIndex: SI): Try<SchematicVertex> {
            return when (sourceIndex) {
                is SourceContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceContainerType(sourceIndex as SourceContainerType<SI, *>)
                }
                is SourceAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forSourceAttribute(sourceIndex as SourceAttribute<SI>)
                }
                is ParameterContainerType<*, *> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forParameterContainerType(sourceIndex as ParameterContainerType<SI, *>)
                }
                is ParameterAttribute<*> -> {
                    @Suppress("UNCHECKED_CAST") //
                    forParameterAttribute(sourceIndex as ParameterAttribute<SI>)
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
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
            sourceContainerType: SourceContainerType<SI, A>
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>> forParameterAttribute(
            parameterAttribute: ParameterAttribute<SI>
        ): Try<SchematicVertex>

        fun <SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
            parameterContainerType: ParameterContainerType<SI, A>
        ): Try<SchematicVertex>
    }
}
