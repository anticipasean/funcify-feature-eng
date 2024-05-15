package funcify.feature.directive

import graphql.language.DirectiveDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import java.util.*

/**
 * Default implementation of the scalar type registry intended to remain internal to this module
 *
 * @author smccarron
 * @created 2022-08-03
 */
internal data class DefaultMaterializationDirectiveRegistry(
    private val materializationDirectivesByName: Map<String, MaterializationDirective> = mapOf()
) : MaterializationDirectiveRegistry {

    private val referencedInputObjectTypeDefinitionsByName:
        Map<String, InputObjectTypeDefinition> by lazy {
        materializationDirectivesByName.values
            .asSequence()
            .map(MaterializationDirective::referencedInputObjectTypeDefinitions)
            .flatMap(List<InputObjectTypeDefinition>::asSequence)
            .groupBy(InputObjectTypeDefinition::getName)
            .asSequence()
            .map { (name: String, itds: List<InputObjectTypeDefinition>) -> name to itds[0] }
            .toMap()
    }

    private val referencedEnumTypeDefinitionsByName: Map<String, EnumTypeDefinition> by lazy {
        materializationDirectivesByName.values
            .asSequence()
            .map(MaterializationDirective::referencedEnumTypeDefinitions)
            .flatMap(List<EnumTypeDefinition>::asSequence)
            .groupBy(EnumTypeDefinition::getName)
            .asSequence()
            .map { (name: String, etds: List<EnumTypeDefinition>) -> name to etds[0] }
            .toMap()
    }

    override fun registerMaterializationDirective(
        materializationDirective: MaterializationDirective
    ): MaterializationDirectiveRegistry {
        return copy(
            materializationDirectivesByName =
                (materializationDirectivesByName as? SortedMap<String, MaterializationDirective>
                        ?: materializationDirectivesByName.toSortedMap())
                    .apply { put(materializationDirective.name, materializationDirective) }
        )
    }

    override fun getAllMaterializationDirectives(): List<MaterializationDirective> {
        return materializationDirectivesByName.values.toList()
    }

    override fun getAllDirectiveDefinitions(): List<DirectiveDefinition> {
        return materializationDirectivesByName.values
            .asSequence()
            .map(MaterializationDirective::directiveDefinition)
            .toList()
    }

    override fun getAllReferencedInputObjectTypeDefinitions(): List<InputObjectTypeDefinition> {
        return referencedInputObjectTypeDefinitionsByName.values.toList()
    }

    override fun getAllReferencedEnumTypeDefinitions(): List<EnumTypeDefinition> {
        return referencedEnumTypeDefinitionsByName.values.toList()
    }

    override fun getDirectiveDefinitionWithName(name: String): DirectiveDefinition? {
        return materializationDirectivesByName[name]?.directiveDefinition
    }

    override fun getReferencedInputObjectTypeDefinitionWithName(
        name: String
    ): InputObjectTypeDefinition? {
        return referencedInputObjectTypeDefinitionsByName[name]
    }

    override fun getReferencedEnumTypeDefinitionWithName(name: String): EnumTypeDefinition? {
        return referencedEnumTypeDefinitionsByName[name]
    }
}
