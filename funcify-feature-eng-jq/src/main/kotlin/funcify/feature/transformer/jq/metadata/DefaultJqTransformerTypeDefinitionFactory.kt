package funcify.feature.transformer.jq.metadata

import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerTypeDefinitionFactory
import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment
import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment.Companion.DEFAULT_DESCRIPTION_FORMAT
import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment.Companion.DEFAULT_INPUT_ARGUMENT_NAME
import funcify.feature.transformer.jq.env.JqTransformerTypeDefinitionEnvironment.Companion.QUERY_OBJECT_TYPE_NAME
import graphql.GraphQLError
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.InputValueDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.language.TypeDefinition
import graphql.language.TypeName
import graphql.schema.idl.TypeDefinitionRegistry
import java.util.*

internal object DefaultJqTransformerTypeDefinitionFactory : JqTransformerTypeDefinitionFactory {

    override fun createTypeDefinitionRegistry(
        environment: JqTransformerTypeDefinitionEnvironment
    ): Try<TypeDefinitionRegistry> {
        return environment.jqTransformers
            .asSequence()
            .map { t: JqTransformer ->
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
                ObjectTypeDefinition.newObjectTypeDefinition()
                    .name(
                        environment.transformerSourceName.replaceFirstChar { c: Char ->
                            c.uppercase()
                        }
                    )
            ) { otdb: ObjectTypeDefinition.Builder, fd: FieldDefinition ->
                otdb.fieldDefinition(fd)
            }
            .build()
            .let { jqTypeDef: ObjectTypeDefinition ->
                sequenceOf(
                        jqTypeDef,
                        ObjectTypeDefinition.newObjectTypeDefinition()
                            .name(QUERY_OBJECT_TYPE_NAME)
                            .fieldDefinition(
                                FieldDefinition.newFieldDefinition()
                                    .name(environment.transformerSourceName)
                                    .type(TypeName.newTypeName(jqTypeDef.name).build())
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
    }

    private fun createInputValueDefinitionForTransformer(
        transformer: JqTransformer
    ): InputValueDefinition {
        return InputValueDefinition.newInputValueDefinition()
            .name(DEFAULT_INPUT_ARGUMENT_NAME)
            .type(transformer.graphQLSDLInputType)
            .build()
    }
}
