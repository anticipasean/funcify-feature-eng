package funcify.feature.transformer.jq.metadata

import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.transformer.jq.JacksonJqTransformer
import graphql.GraphQLError
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.InputValueDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.SourceLocation
import graphql.language.TypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import java.util.*

internal object TransformerTypeDefinitionRegistryCreator :
    (Iterable<JacksonJqTransformer>) -> TypeDefinitionRegistry {

    private const val DEFAULT_INPUT_ARGUMENT_NAME = "input"
    private const val DEFAULT_DESCRIPTION_FORMAT = "jq [ expression: \"%s\" ]"
    private const val JQ_TRANSFORMER_OBJECT_TYPE_NAME = "Jq"
    private const val TRANSFORMER_OBJECT_TYPE_NAME = "Transformer"
    private const val QUERY_OBJECT_TYPE_NAME = "Query"

    override fun invoke(transformers: Iterable<JacksonJqTransformer>): TypeDefinitionRegistry {
        return transformers
            .asSequence()
            .map { t: JacksonJqTransformer ->
                FieldDefinition.newFieldDefinition()
                    .name(t.name)
                    .type(t.graphQLSDLOutputType)
                    .inputValueDefinition(createInputValueDefinitionForTransformer(t))
                    .description(
                        Description(
                            DEFAULT_DESCRIPTION_FORMAT.format(t.expression),
                            SourceLocation.EMPTY,
                            System.lineSeparator() in t.expression
                        )
                    )
                    .build()
            }
            .fold(
                ObjectTypeDefinition.newObjectTypeDefinition().name(JQ_TRANSFORMER_OBJECT_TYPE_NAME)
            ) { otdb: ObjectTypeDefinition.Builder, fd: FieldDefinition ->
                otdb.fieldDefinition(fd)
            }
            .build()
            .let { jqTypeDef: ObjectTypeDefinition ->
                sequenceOf(
                        jqTypeDef,
                        ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
                            .name(TRANSFORMER_OBJECT_TYPE_NAME)
                            .fieldDefinition(
                                FieldDefinition.newFieldDefinition()
                                    .name(jqTypeDef.name.lowercase())
                                    .type(TypeName.newTypeName(jqTypeDef.name).build())
                                    .build()
                            )
                            .build(),
                        ObjectTypeExtensionDefinition.newObjectTypeExtensionDefinition()
                            .name(QUERY_OBJECT_TYPE_NAME)
                            .fieldDefinition(
                                FieldDefinition.newFieldDefinition()
                                    .name(TRANSFORMER_OBJECT_TYPE_NAME.lowercase())
                                    .type(
                                        TypeName.newTypeName(TRANSFORMER_OBJECT_TYPE_NAME).build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .fold(Try.success(TypeDefinitionRegistry())) {
                        accumulateResult: Try<TypeDefinitionRegistry>,
                        td: TypeDefinition<*> ->
                        accumulateResult.flatMap { tdr: TypeDefinitionRegistry ->
                            val possibleError: Optional<GraphQLError> = tdr.add(td)
                            when {
                                possibleError.isPresent -> {
                                    Try.failure<TypeDefinitionRegistry>(
                                        ServiceError.of(
                                            "type_definition error: [ %s ]",
                                            possibleError.get().toSpecification()
                                        )
                                    )
                                }
                                else -> {
                                    Try.success(tdr)
                                }
                            }
                        }
                    }
            }
            .orElseThrow()
    }

    private fun createInputValueDefinitionForTransformer(
        transformer: JacksonJqTransformer
    ): InputValueDefinition {
        return InputValueDefinition.newInputValueDefinition()
            .name(DEFAULT_INPUT_ARGUMENT_NAME)
            .type(transformer.graphQLSDLInputType)
            .build()
    }
}
