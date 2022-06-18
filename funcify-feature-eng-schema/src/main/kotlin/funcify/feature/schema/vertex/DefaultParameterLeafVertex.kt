package funcify.feature.schema.vertex

import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.index.CompositeParameterAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
internal data class DefaultParameterLeafVertex(
    override val path: SchematicPath,
    override val compositeParameterAttribute: CompositeParameterAttribute
) : ParameterLeafVertex {

    init {
        if (path.arguments.isEmpty() && path.directives.isEmpty()) {
            throw SchemaException(
                SchemaErrorResponse.SCHEMATIC_INTEGRITY_VIOLATION,
                """the path for a parameter vertex must contain 
                   |at least one argument or directive 
                   |[ provided_path: $path ]""".flattenIntoOneLine()
            )
        }
    }
}
