package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.StringValue
import graphql.language.TypeName

object AliasDirective : MaterializationDirective {

    override val name: String = "alias"

    override val description: String =
        """Indicates a different name that 
            |the corresponding argument or field_definition 
            |may have when specified as a field_name 
            |in an "output" variable"""
            .trimMargin()

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        listOf(
                Introspection.DirectiveLocation.FIELD_DEFINITION,
                Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION,
                Introspection.DirectiveLocation.ARGUMENT_DEFINITION
            )
            .map { iDirLoc -> DirectiveLocation(iDirLoc.name) }
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        listOf<InputValueDefinition>(
            InputValueDefinition.newInputValueDefinition()
                .name("name")
                .type(
                    NonNullType.newNonNullType(
                            TypeName.newTypeName().name(Scalars.GraphQLString.name).build()
                        )
                        .build()
                )
                .defaultValue(StringValue.of(""))
                .build()
        )
    }

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .name(name)
            .repeatable(true)
            .description(
                Description(
                    description,
                    SourceLocation.EMPTY,
                    description.contains(System.lineSeparator())
                )
            )
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .build()
    }
}
