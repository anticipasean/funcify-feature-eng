package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.*

object SubtypingDirective : MaterializationDirective {

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
                |that will have one of the @discriminator-directive 
                |values, required if SUBTYPE_FIELD_VALUE strategy is selected"""
                .trimMargin()
        listOf(
            InputValueDefinition.newInputValueDefinition()
                .name("strategy")
                .type(NonNullType.newNonNullType(TypeName("SubtypingStrategy")).build())
                .defaultValue(EnumValue.of("SUBTYPE_FIELD_NAME"))
                .build(),
            InputValueDefinition.newInputValueDefinition()
                .name("discriminatorFieldName")
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
            """@discriminator directive on annotated object subtype 
                |contains the name of the field 
                |unique to the subtype to be used to select 
                |that subtype when resolving parent interface type"""
                .trimMargin()
        val subtypeFieldValueDescription: String =
            """@discriminator directive on annotated object subtype 
                |contains the value of the field 
                |unique to the subtype to be used to select 
                |that subtype when resolving parent interface type"""
                .trimMargin()
        listOf(
            EnumTypeDefinition.newEnumTypeDefinition()
                .name("SubtypingStrategy")
                .enumValueDefinitions(
                    listOf(
                        EnumValueDefinition.newEnumValueDefinition()
                            .name("SUBTYPE_FIELD_NAME")
                            .description(
                                Description(
                                    subtypeFieldNameDescription,
                                    SourceLocation.EMPTY,
                                    subtypeFieldNameDescription.contains(System.lineSeparator())
                                )
                            )
                            .build(),
                        EnumValueDefinition.newEnumValueDefinition()
                            .name("SUBTYPE_FIELD_VALUE")
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
