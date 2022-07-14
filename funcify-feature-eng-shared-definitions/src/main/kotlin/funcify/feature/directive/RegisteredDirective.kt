package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.TypeName

object RegisteredDirective : MaterializationDirective {

    override val name: String = "registered"

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        listOf(Introspection.DirectiveLocation.FIELD_DEFINITION).map { iDirLoc ->
            DirectiveLocation.newDirectiveLocation().name(iDirLoc.name).build()
        }
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        listOf<InputValueDefinition>(
            InputValueDefinition.newInputValueDefinition()
                .name("status")
                .type(NonNullType.newNonNullType(TypeName(Scalars.GraphQLString.name)).build())
                .build(),
            InputValueDefinition.newInputValueDefinition()
                .name("valueType")
                .type(NonNullType.newNonNullType(TypeName(Scalars.GraphQLString.name)).build())
                .build(),
            InputValueDefinition.newInputValueDefinition()
                .name("defaultValue")
                .type(NonNullType.newNonNullType(TypeName(Scalars.GraphQLString.name)).build())
                .build()
        )
    }

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .name(name)
            .repeatable(false)
            .description(
                Description(
                    """Indicates a field has a corresponding entry in the
                       |feature registry wired at the startup of this application 
                       |and provides information regarding what implementation status 
                       |it has
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
