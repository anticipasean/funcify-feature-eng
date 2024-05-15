package funcify.feature.directive

import graphql.introspection.Introspection
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.SourceLocation

object EntityKeyDirective : MaterializationDirective {

    /**
     * Note: Name of this directive chosen to avoid conflict with other GraphQL frameworks e.g. Apollo
     * that use `@key`, `@id`
     */
    override val name: String = "entityKey"

    override val description: String =
        """Indicates field explicitly should be used in 
        |looking up instances of entity"""
            .trimMargin()

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        sequenceOf(Introspection.DirectiveLocation.FIELD_DEFINITION)
            .map { dl: Introspection.DirectiveLocation ->
                DirectiveLocation.newDirectiveLocation().name(dl.name).build()
            }
            .toList()
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy { emptyList() }

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .name(name)
            .directiveLocations(supportedDirectiveLocations)
            .description(
                Description(
                    description,
                    SourceLocation.EMPTY,
                    description.contains(System.lineSeparator())
                )
            )
            .inputValueDefinitions(inputValueDefinitions)
            .repeatable(false)
            .build()
    }
}
