package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.factory.MetamodelGraphCreationContext
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexGraphSourceIndexBasedRemappingStrategy :
    SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext> {

    val remappingHandler: SchematicVertexSourceIndexBasedRemappingHandler

    override fun canBeAppliedTo(
        context: MetamodelGraphCreationContext,
        schematicVertex: SchematicVertex
    ): Boolean

    override fun applyToVertexInContext(
        context: MetamodelGraphCreationContext,
        schematicVertex: SchematicVertex,
    ): Try<MetamodelGraphCreationContext> {
        return Try.attempt { remappingHandler.onSchematicVertex(schematicVertex, context) }
    }
}
