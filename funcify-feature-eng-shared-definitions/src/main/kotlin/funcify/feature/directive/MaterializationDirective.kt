package funcify.feature.directive

import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputValueDefinition

interface MaterializationDirective {

    val name: String

    val description: String

    val supportedDirectiveLocations: List<DirectiveLocation>

    val inputValueDefinitions: List<InputValueDefinition>

    /**
     * Any [InputObjectTypeDefinition]s referenced in one or more of the [InputValueDefinition]s for
     * this directive that must be present when creating the SDL [DirectiveDefinition]
     * @default_value: an empty list if no input_object_definitions are needed
     */
    val referencedInputObjectTypeDefinitions: List<InputObjectTypeDefinition>
        get() = emptyList()

    /**
     * Any [EnumTypeDefinition]s referenced in one or more of the [InputValueDefinition]s for this
     * directive that must be present when creating the SDL [DirectiveDefinition]
     * @default_value: an empty list if no enum_type_definitions are needed
     */
    val referencedEnumTypeDefinitions: List<EnumTypeDefinition>
        get() = emptyList()

    val directiveDefinition: DirectiveDefinition
}
