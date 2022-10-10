package funcify.feature.materializer.directive

import funcify.feature.directive.MaterializationDirective
import graphql.introspection.Introspection.DirectiveLocation.QUERY
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.SourceLocation

/**
 *
 * @author smccarron
 * @created 2022-10-08
 */
object FlattenDirective : MaterializationDirective {

    override val name: String = "flatten"

    override val description: String = "Flattens query.data into key-value list"

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        listOf(QUERY).map { dirLoc ->
            DirectiveLocation.newDirectiveLocation().name(dirLoc.name).build()
        }
    }

    override val inputValueDefinitions: List<InputValueDefinition> = listOf()

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .name(name)
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .description(
                Description(
                    description,
                    SourceLocation.EMPTY,
                    description.contains(System.lineSeparator())
                )
            )
            .repeatable(false)
            .build()
    }
}
