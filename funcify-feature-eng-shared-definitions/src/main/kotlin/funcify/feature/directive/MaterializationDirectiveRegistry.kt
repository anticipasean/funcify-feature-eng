package funcify.feature.directive

import graphql.language.DirectiveDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.schema.GraphQLScalarType
import java.util.*

/**
 * Registry of the [GraphQLScalarType]s to be used throughout a given service or GraphQL setup
 *
 * @author smccarron
 * @created 2022-08-03
 */
interface MaterializationDirectiveRegistry {

    companion object {

        @JvmStatic
        fun createEmptyRegistry(): MaterializationDirectiveRegistry {
            return DefaultMaterializationDirectiveRegistry()
        }

        @JvmStatic
        fun createCustomRegistryFrom(
            materializationDirectives: Iterable<MaterializationDirective>
        ): MaterializationDirectiveRegistry {
            return DefaultMaterializationDirectiveRegistry(
                materializationDirectives.fold(sortedMapOf(Comparator.naturalOrder())) {
                    mdByName: SortedMap<String, MaterializationDirective>,
                    md: MaterializationDirective ->
                    mdByName.apply { put(md.name, md) }
                }
            )
        }

        @JvmStatic
        fun createStandardRegistry(): MaterializationDirectiveRegistry {
            return createCustomRegistryFrom(
                sequenceOf(
                        AliasDirective,
                        LastUpdatedDirective,
                        SubtypingDirective,
                        DiscriminatorDirective,
                        TransformDirective,
                        EntityKeyDirective
                    )
                    .asIterable()
            )
        }
    }

    fun registerMaterializationDirective(
        materializationDirective: MaterializationDirective
    ): MaterializationDirectiveRegistry

    fun getAllMaterializationDirectives(): List<MaterializationDirective>

    fun getAllDirectiveDefinitions(): List<DirectiveDefinition>

    fun getAllReferencedInputObjectTypeDefinitions(): List<InputObjectTypeDefinition>

    fun getAllReferencedEnumTypeDefinitions(): List<EnumTypeDefinition>

    fun getDirectiveDefinitionWithName(name: String): DirectiveDefinition?

    fun getReferencedInputObjectTypeDefinitionWithName(name: String): InputObjectTypeDefinition?

    fun getReferencedEnumTypeDefinitionWithName(name: String): EnumTypeDefinition?
}
