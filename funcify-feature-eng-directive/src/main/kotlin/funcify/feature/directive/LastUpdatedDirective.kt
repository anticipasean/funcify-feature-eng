package funcify.feature.directive

import graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.SourceLocation
import kotlinx.collections.immutable.persistentListOf

object LastUpdatedDirective : MaterializationDirective {

    override val name: String = "last_updated"

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        listOf(FIELD_DEFINITION, INPUT_FIELD_DEFINITION).fold(persistentListOf()) { pl, iDirLoc ->
            pl.add(DirectiveLocation.newDirectiveLocation().name(iDirLoc.name).build())
        }
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy { persistentListOf() }

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .name(name)
            .repeatable(false)
            .description(
                Description(
                    """Indicates temporal field represents latest datetime \\
                       |at which the given object type instance is current. \\
                       |There should at most be one field within a given \\
                       |object type definition that represents \\
                       |the latest timestamp
                       |"""
                        .trimMargin()
                        .replace("\\\\", "\n"),
                    SourceLocation.EMPTY,
                    true
                )
            )
            .build()
    }
}
