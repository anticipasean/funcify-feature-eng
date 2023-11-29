package funcify.feature.directive

import graphql.Scalars
import graphql.introspection.Introspection
import graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import graphql.language.Description
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveLocation
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputValueDefinition
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.TypeName

object TransformDirective : MaterializationDirective {

    const val FIELD_COORDINATES_INPUT_OBJECT_TYPE_DEFINITION_NAME: String = "FieldCoordinates"
    const val COORDINATES_INPUT_VALUE_DEFINITION_NAME: String = "coordinates"
    const val TYPE_NAME_INPUT_VALUE_DEFINITION_NAME: String = "typeName"
    const val FIELD_NAME_INPUT_VALUE_DEFINITION_NAME: String = "fieldName"

    override val name: String = "transform"

    override val description: String =
        """Indicates what transformer definition should be applied 
        |to the input argument value(s) of this feature field definition"""
            .trimMargin()

    override val supportedDirectiveLocations: List<DirectiveLocation> by lazy {
        sequenceOf(FIELD_DEFINITION)
            .map { dl: Introspection.DirectiveLocation ->
                DirectiveLocation.newDirectiveLocation().name(dl.name).build()
            }
            .toList()
    }

    override val referencedInputObjectTypeDefinitions: List<InputObjectTypeDefinition> by lazy {
        val nonNullStringTypeName: NonNullType =
            NonNullType.newNonNullType(
                    TypeName.newTypeName().name(Scalars.GraphQLString.name).build()
                )
                .build()
        val coordinatesDescription: String =
            "Specifies location of field definition within GraphQL schema"
        val typeNameDescription: String = "object type name to which field definition belongs"
        val fieldNameDescription: String = "name of the field definition found on object type"
        sequenceOf(
                InputObjectTypeDefinition.newInputObjectDefinition()
                    .name(FIELD_COORDINATES_INPUT_OBJECT_TYPE_DEFINITION_NAME)
                    .description(
                        Description(
                            coordinatesDescription,
                            SourceLocation.EMPTY,
                            coordinatesDescription.contains(System.lineSeparator())
                        )
                    )
                    .inputValueDefinitions(
                        sequenceOf(
                                InputValueDefinition.newInputValueDefinition()
                                    .name(TYPE_NAME_INPUT_VALUE_DEFINITION_NAME)
                                    .type(nonNullStringTypeName)
                                    .description(
                                        Description(
                                            typeNameDescription,
                                            SourceLocation.EMPTY,
                                            typeNameDescription.contains(System.lineSeparator())
                                        )
                                    )
                                    .build(),
                                InputValueDefinition.newInputValueDefinition()
                                    .name(FIELD_NAME_INPUT_VALUE_DEFINITION_NAME)
                                    .type(nonNullStringTypeName)
                                    .description(
                                        Description(
                                            fieldNameDescription,
                                            SourceLocation.EMPTY,
                                            fieldNameDescription.contains(System.lineSeparator())
                                        )
                                    )
                                    .build()
                            )
                            .toList()
                    )
                    .build()
            )
            .toList()
    }

    override val inputValueDefinitions: List<InputValueDefinition> by lazy {
        val coordinatesDescription: String =
            """Specifies the coordinates within the Transformers object tree""".trimMargin()
        sequenceOf(
                InputValueDefinition.newInputValueDefinition()
                    .name(COORDINATES_INPUT_VALUE_DEFINITION_NAME)
                    .description(
                        Description(
                            coordinatesDescription,
                            SourceLocation.EMPTY,
                            coordinatesDescription.contains(System.lineSeparator())
                        )
                    )
                    .type(
                        NonNullType.newNonNullType()
                            .type(
                                TypeName.newTypeName(FIELD_COORDINATES_INPUT_OBJECT_TYPE_DEFINITION_NAME)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .toList()
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
            .inputValueDefinitions(inputValueDefinitions)
            .directiveLocations(supportedDirectiveLocations)
            .build()
    }
}
