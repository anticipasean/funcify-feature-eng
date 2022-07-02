package funcify.feature.datasource.sdl

import funcify.feature.schema.vertex.SchematicGraphVertexType
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-06-29
 */
interface SchematicGraphVertexTypeBasedSDLDefinitionStrategy<T : Any> :
    SchematicVertexSDLDefinitionStrategy<T> {

    val applicableSchematicGraphVertexTypes: ImmutableSet<SchematicGraphVertexType>

    override fun canBeAppliedToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Boolean {
        return SchematicGraphVertexType.getSchematicGraphTypeForVertexSubtype(
                context.currentVertex::class
            )
            .isDefined()
    }
}
