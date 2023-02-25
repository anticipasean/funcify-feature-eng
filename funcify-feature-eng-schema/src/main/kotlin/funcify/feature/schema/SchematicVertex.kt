package funcify.feature.schema

import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try

/**
 * Represents a node within a feature function graph
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicVertex {

    val path: SchematicPath

    interface Builder {

        fun updatePath(transformer: (SchematicPath.Builder) -> SchematicPath.Builder): Builder

        fun addSourceAttribute(sourceAttribute: SourceAttribute<*>): Builder

        fun removeSourceAttribute(sourceAttribute: SourceAttribute<*>): Builder

        fun addSourceContainerType(sourceContainerType: SourceContainerType<*, *>): Builder

        fun removeSourceContainerType(sourceContainerType: SourceContainerType<*, *>): Builder

        fun addParameterAttribute(parameterAttribute: ParameterAttribute<*>): Builder

        fun removeParameterAttribute(parameterAttribute: ParameterAttribute<*>): Builder

        fun addParameterContainerType(parameterContainerType: ParameterContainerType<*, *>): Builder

        fun removeParameterContainerType(
            parameterContainerType: ParameterContainerType<*, *>
        ): Builder

        fun clearSourceIndices(): Builder

        fun build(): Try<SchematicVertex>
    }
}
