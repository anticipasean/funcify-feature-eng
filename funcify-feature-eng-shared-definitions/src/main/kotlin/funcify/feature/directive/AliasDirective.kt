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
import kotlinx.collections.immutable.persistentListOf

object AliasDirective : MaterializationDirective {

    override val name: String = "alias"

    const val INPUT_ARGUMENT_NAME: String = "name"

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        listOf(
                Introspection.DirectiveLocation.FIELD_DEFINITION,
                Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION
            )
            .fold(persistentListOf()) { pl, iDirLoc -> pl.add(DirectiveLocation(iDirLoc.name)) }
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        listOf<InputValueDefinition>(
            InputValueDefinition.newInputValueDefinition()
                .name(INPUT_ARGUMENT_NAME)
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
                    "Indicates a different name that the field may have",
                    SourceLocation.EMPTY,
                    false
                )
            )
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .build()
    }
}
