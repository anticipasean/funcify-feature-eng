package funcify.feature.schema.vertex

import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine

internal data class DefaultSourceJunctionVertex(
    override val path: SchematicPath,
    override val compositeContainerType: CompositeSourceContainerType,
    override val compositeAttribute: CompositeSourceAttribute
) : SourceJunctionVertex {

    init {
        if (path.isRoot() || !path.arguments.isEmpty() || !path.directives.isEmpty()) {
            throw SchemaException(
                SchemaErrorResponse.SCHEMATIC_INTEGRITY_VIOLATION,
                """the path for a source junction vertex cannot be root 
                   |i.e. must have at least one path segment; 
                   |and cannot have any arguments or directives 
                   |[ provided_path: $path ]""".flattenIntoOneLine()
            )
        }
    }
}
