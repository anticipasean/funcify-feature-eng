package funcify.feature.directive

import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition

interface MaterializationDirective {

    val name: String

    val supportedDirectiveLocations: List<DirectiveLocation>

    val inputValueDefinitions: List<InputValueDefinition>

    val directiveDefinition: DirectiveDefinition

}
