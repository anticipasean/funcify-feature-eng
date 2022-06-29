package funcify.feature.schema.vertex

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.schema.SchematicVertex
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet

/**
 * Enum enabling graph vertex types to be handled in `when(graphVertexType) {...}` case structures without
 * compromising the API, as "sealing" vertex types would likely make the API less flexible while
 * it's still undergoing lots of changes
 * @author smccarron
 * @created 2022-06-29
 */
enum class SchematicGraphVertexType(val graphVertexSubtype: KClass<out SchematicVertex>) {
    SOURCE_ROOT_VERTEX(SourceRootVertex::class),
    SOURCE_JUNCTION_VERTEX(SourceJunctionVertex::class),
    SOURCE_LEAF_VERTEX(SourceLeafVertex::class),
    PARAMETER_JUNCTION_VERTEX(ParameterJunctionVertex::class),
    PARAMETER_LEAF_VERTEX(ParameterLeafVertex::class);

    companion object {
        val schematicGraphVertexTypeByVertexSubtype:
            ImmutableMap<KClass<out SchematicVertex>, SchematicGraphVertexType> by lazy {
            SchematicGraphVertexType.values().fold(persistentMapOf()) { pm, sgvt ->
                pm.put(sgvt.graphVertexSubtype, sgvt)
            }
        }
        fun getSchematicGraphTypeForVertexSubtype(
            vertexSubtype: KClass<out SchematicVertex>
        ): Option<SchematicGraphVertexType> {
            return schematicGraphVertexTypeByVertexSubtype[vertexSubtype].toOption()
        }
        private val containerTypeVertexTypes: ImmutableSet<SchematicGraphVertexType> by lazy {
            SchematicGraphVertexType.values()
                .asSequence()
                .filter { sgvt ->
                    sgvt.graphVertexSubtype.isSubclassOf(SourceContainerTypeVertex::class) ||
                        sgvt.graphVertexSubtype.isSubclassOf(ParameterAttributeVertex::class)
                }
                .toPersistentSet()
        }
        private val attributeVertexTypes: ImmutableSet<SchematicGraphVertexType> by lazy {
            SchematicGraphVertexType.values()
                .asSequence()
                .filter { sgvt ->
                    sgvt.graphVertexSubtype.isSubclassOf(SourceAttributeVertex::class) ||
                        sgvt.graphVertexSubtype.isSubclassOf(ParameterAttributeVertex::class)
                }
                .toPersistentSet()
        }
        private val sourceIndexTypes: ImmutableSet<SchematicGraphVertexType> by lazy {
            SchematicGraphVertexType.values()
                .asSequence()
                .filter { sgvt ->
                    sgvt.graphVertexSubtype.isSubclassOf(SourceContainerTypeVertex::class) ||
                        sgvt.graphVertexSubtype.isSubclassOf(SourceAttributeVertex::class)
                }
                .toPersistentSet()
        }
        private val parameterIndexTypes: ImmutableSet<SchematicGraphVertexType> by lazy {
            SchematicGraphVertexType.values()
                .asSequence()
                .filter { sgvt ->
                    sgvt.graphVertexSubtype.isSubclassOf(ParameterContainerTypeVertex::class) ||
                        sgvt.graphVertexSubtype.isSubclassOf(ParameterAttributeVertex::class)
                }
                .toPersistentSet()
        }
    }

    fun canRepresentContainerType(): Boolean {
        return containerTypeVertexTypes.contains(this)
    }

    fun canRepresentAttributeOnContainerType(): Boolean {
        return attributeVertexTypes.contains(this)
    }

    fun representsValueFromSource(): Boolean {
        return sourceIndexTypes.contains(this)
    }

    fun representsInputParameterToSource(): Boolean {
        return parameterIndexTypes.contains(this)
    }
}
