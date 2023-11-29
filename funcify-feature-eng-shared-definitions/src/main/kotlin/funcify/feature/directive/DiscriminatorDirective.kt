package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputValueDefinition
import graphql.language.NullValue
import graphql.language.SourceLocation
import graphql.language.TypeName
import graphql.scalars.ExtendedScalars

object DiscriminatorDirective : MaterializationDirective {

    const val FIELD_NAME_INPUT_VALUE_DEFINITION_NAME = "fieldName"
    const val FIELD_VALUE_INPUT_VALUE_DEFINITION_NAME = "fieldValue"

    override val name: String = "discriminator"

    override val description: String =
        """Indicates how the @%s.strategy 
            |should be applied to this type. 
            |Either a %s or a %s 
            |should be supplied but not both"""
            .format(
                SubtypingDirective.name,
                FIELD_NAME_INPUT_VALUE_DEFINITION_NAME,
                FIELD_VALUE_INPUT_VALUE_DEFINITION_NAME
            )
            .trimMargin()

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        sequenceOf(Introspection.DirectiveLocation.OBJECT)
            .map { dl: Introspection.DirectiveLocation ->
                DirectiveLocation.newDirectiveLocation().name(dl.name).build()
            }
            .toList()
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        val fieldNameDescription: String =
            """Field name that is unique to this 
                |subtype of the @%s annotated interface 
                |if %s strategy is selected"""
                .format(
                    SubtypingDirective.name,
                    SubtypingDirective.FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE
                )
                .trimMargin()
        val fieldValueDescription: String =
            """Field value that is unique to this 
                |subtype of the @%s annotated interface 
                |if %s strategy is selected 
                |and a %s has been supplied"""
                .format(
                    SubtypingDirective.name,
                    SubtypingDirective.FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE,
                    SubtypingDirective.DISCRIMINATOR_FIELD_NAME_INPUT_VALUE_DEFINITION_NAME
                )
                .trimMargin()
        listOf(
            InputValueDefinition.newInputValueDefinition()
                .name(FIELD_NAME_INPUT_VALUE_DEFINITION_NAME)
                .description(
                    Description(
                        fieldNameDescription,
                        SourceLocation.EMPTY,
                        fieldNameDescription.contains(System.lineSeparator())
                    )
                )
                .type(TypeName.newTypeName(Scalars.GraphQLString.name).build())
                .defaultValue(NullValue.of())
                .build(),
            InputValueDefinition.newInputValueDefinition()
                .name(FIELD_VALUE_INPUT_VALUE_DEFINITION_NAME)
                .description(
                    Description(
                        fieldValueDescription,
                        SourceLocation.EMPTY,
                        fieldValueDescription.contains(System.lineSeparator())
                    )
                )
                .type(TypeName.newTypeName(ExtendedScalars.Json.name).build())
                .defaultValue(NullValue.of())
                .build()
        )
    }

    override val directiveDefinition: DirectiveDefinition by lazy {
        DirectiveDefinition.newDirectiveDefinition()
            .name(name)
            .description(
                Description(
                    description,
                    SourceLocation.EMPTY,
                    description.contains(System.lineSeparator())
                )
            )
            .repeatable(false)
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .build()
    }
}
