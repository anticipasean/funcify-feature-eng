package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexGraphBasedMappingTemplate<C> {

    fun onSchematicVertex(schematicVertex: SchematicVertex, context: C): C {
        return when (schematicVertex) {
            is SourceRootVertex -> onSourceRootVertex(schematicVertex, context)
            is SourceJunctionVertex -> onSourceJunctionVertex(schematicVertex, context)
            is SourceLeafVertex -> onSourceLeafVertex(schematicVertex, context)
            is ParameterJunctionVertex -> onParameterJunctionVertex(schematicVertex, context)
            is ParameterLeafVertex -> onParameterLeafVertex(schematicVertex, context)
            else -> {
                val schematicGraphTypeForVertexSubtype =
                    SchematicGraphVertexType.getSchematicGraphTypeForVertexSubtype(
                        schematicVertex::class
                    )
                val message =
                    """unhandled schematic_vertex type: [ actual: { ${schematicVertex::class.qualifiedName}, 
                        |$schematicGraphTypeForVertexSubtype 
                        |]""".flattenIntoOneLine()
                throw SchemaException(SchemaErrorResponse.UNEXPECTED_ERROR, message)
            }
        }
    }

    fun onSourceRootVertex(sourceRootVertex: SourceRootVertex, context: C): C

    fun onSourceJunctionVertex(sourceJunctionVertex: SourceJunctionVertex, context: C): C

    fun onSourceLeafVertex(sourceLeafVertex: SourceLeafVertex, context: C): C

    fun onParameterJunctionVertex(parameterJunctionVertex: ParameterJunctionVertex, context: C): C

    fun onParameterLeafVertex(parameterLeafVertex: ParameterLeafVertex, context: C): C
}
