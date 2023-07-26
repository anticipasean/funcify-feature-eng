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

    override val name: String = "discriminator"

    override val description: String =
        """Indicates how the @subtyping.strategy 
            |should be applied to this type. 
            |Either a fieldName or a fieldValue 
            |should be supplied but not both"""
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
                |subtype of the @subtyping annotated interface 
                |if SUBTYPE_FIELD_NAME strategy is selected"""
                .trimMargin()
        val fieldValueDescription: String =
            """Field value that is unique to this 
                |subtype of the @subtyping annotated interface 
                |if SUBTYPE_FIELD_VALUE strategy is selected 
                |and a discriminatorFieldName has been supplied"""
                .trimMargin()
        listOf(
            InputValueDefinition.newInputValueDefinition()
                .name("fieldName")
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
                .name("fieldValue")
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
            .repeatable(false)
            .directiveLocations(supportedDirectiveLocations)
            .inputValueDefinitions(inputValueDefinitions)
            .build()
    }
}
