package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.EnumTypeDefinition
import graphql.language.EnumValue
import graphql.language.EnumValueDefinition
import graphql.language.InputValueDefinition
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.TypeName

object SubtypingDirective : MaterializationDirective {

    const val STRATEGY_INPUT_VALUE_DEFINITION_NAME: String = "strategy"
    const val STRATEGY_INPUT_OBJECT_TYPE_DEFINITION_NAME: String = "SubtypingStrategy"
    const val FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE: String = "SUBTYPE_FIELD_NAME"
    const val FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE: String = "SUBTYPE_FIELD_VALUE"
    const val DISCRIMINATOR_FIELD_NAME_INPUT_VALUE_DEFINITION_NAME: String =
        "discriminatorFieldName"

    override val name: String = "subtyping"

    override val description: String =
        """Indicates how an interface type may be resolved into an object type"""

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        sequenceOf(Introspection.DirectiveLocation.INTERFACE)
            .map { dl: Introspection.DirectiveLocation ->
                DirectiveLocation.newDirectiveLocation().name(dl.name).build()
            }
            .toList()
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        val propertyDescription: String =
            """Name of field on parent interface 
                |that will have one of the @%s-directive 
                |values, required if %s strategy is selected"""
                .format(DiscriminatorDirective.name, FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE)
                .trimMargin()
        listOf(
            InputValueDefinition.newInputValueDefinition()
                .name(STRATEGY_INPUT_VALUE_DEFINITION_NAME)
                .type(
                    NonNullType.newNonNullType(TypeName(STRATEGY_INPUT_OBJECT_TYPE_DEFINITION_NAME))
                        .build()
                )
                .defaultValue(EnumValue.of(FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE))
                .build(),
            InputValueDefinition.newInputValueDefinition()
                .name(DISCRIMINATOR_FIELD_NAME_INPUT_VALUE_DEFINITION_NAME)
                .type(TypeName.newTypeName(Scalars.GraphQLString.name).build())
                .description(
                    Description(
                        propertyDescription,
                        SourceLocation.EMPTY,
                        propertyDescription.contains(System.lineSeparator())
                    )
                )
                .build()
        )
    }

    override val referencedEnumTypeDefinitions: List<EnumTypeDefinition> by lazy {
        val subtypeFieldNameDescription: String =
            """@%s directive on annotated object subtype 
                |contains the name of the field 
                |unique to the subtype to be used to select 
                |that subtype when resolving parent interface type"""
                .format(DiscriminatorDirective.name)
                .trimMargin()
        val subtypeFieldValueDescription: String =
            """@%s directive on annotated object subtype 
                |contains the value of the field 
                |unique to the subtype to be used to select 
                |that subtype when resolving parent interface type"""
                .format(DiscriminatorDirective.name)
                .trimMargin()
        listOf(
            EnumTypeDefinition.newEnumTypeDefinition()
                .name(STRATEGY_INPUT_OBJECT_TYPE_DEFINITION_NAME)
                .enumValueDefinitions(
                    listOf(
                        EnumValueDefinition.newEnumValueDefinition()
                            .name(FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE)
                            .description(
                                Description(
                                    subtypeFieldNameDescription,
                                    SourceLocation.EMPTY,
                                    subtypeFieldNameDescription.contains(System.lineSeparator())
                                )
                            )
                            .build(),
                        EnumValueDefinition.newEnumValueDefinition()
                            .name(FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE)
                            .description(
                                Description(
                                    subtypeFieldValueDescription,
                                    SourceLocation.EMPTY,
                                    subtypeFieldValueDescription.contains(System.lineSeparator())
                                )
                            )
                            .build()
                    )
                )
                .build()
        )
    }

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
            .repeatable(false)
            .inputValueDefinitions(inputValueDefinitions)
            .build()
    }
}
