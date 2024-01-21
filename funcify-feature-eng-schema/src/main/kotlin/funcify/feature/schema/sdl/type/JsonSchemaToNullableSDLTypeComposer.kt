package funcify.feature.schema.sdl.type

import arrow.core.Either
import arrow.core.Option
import arrow.core.compose
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.Scalars
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.scalars.ExtendedScalars

object JsonSchemaToNullableSDLTypeComposer : (JsonSchema) -> Type<*> {

    override fun invoke(jsonSchema: JsonSchema): Type<*> {
        return TypeCompositionContext(outerSchema = jsonSchema)
            .toOption()
            .recurse { c: TypeCompositionContext -> composeType(c) }
            .successIfDefined {
                ServiceError.of(
                    "unable to determine GraphQL SDL type from json_schema: [ %s ]",
                    jsonSchema
                )
            }
            .orElseThrow()
    }

    private data class TypeCompositionContext(
        val outerSchema: JsonSchema,
        val innerSchema: Option<JsonSchema> = none<JsonSchema>(),
        val compositionLevel: Int = 0,
        val compositionFunction: (Type<*>) -> Type<*> = ::identity,
    )

    private fun composeType(
        context: TypeCompositionContext
    ): Option<Either<TypeCompositionContext, Type<*>>> {
        val innerSchema: JsonSchema = context.innerSchema.orNull() ?: context.outerSchema
        return when (innerSchema.type) {
            JsonFormatTypes.NULL -> {
                none<Either<TypeCompositionContext, Type<*>>>()
            }
            JsonFormatTypes.STRING -> {
                when (innerSchema.asStringSchema().format) {
                    JsonValueFormat.DATE -> {
                        context.compositionFunction
                            .invoke(TypeName.newTypeName(ExtendedScalars.Date.name).build())
                            .right()
                            .some()
                    }
                    JsonValueFormat.DATE_TIME -> {
                        context.compositionFunction
                            .invoke(TypeName.newTypeName(ExtendedScalars.DateTime.name).build())
                            .right()
                            .some()
                    }
                    JsonValueFormat.UUID -> {
                        context.compositionFunction
                            .invoke(TypeName.newTypeName(ExtendedScalars.UUID.name).build())
                            .right()
                            .some()
                    }
                    else -> {
                        context.compositionFunction
                            .invoke(TypeName.newTypeName(Scalars.GraphQLString.name).build())
                            .right()
                            .some()
                    }
                }
            }
            JsonFormatTypes.NUMBER -> {
                context.compositionFunction
                    .invoke(TypeName.newTypeName(Scalars.GraphQLFloat.name).build())
                    .right()
                    .some()
            }
            JsonFormatTypes.INTEGER -> {
                context.compositionFunction
                    .invoke(TypeName.newTypeName(Scalars.GraphQLInt.name).build())
                    .right()
                    .some()
            }
            JsonFormatTypes.BOOLEAN -> {
                context.compositionFunction
                    .invoke(TypeName.newTypeName(Scalars.GraphQLBoolean.name).build())
                    .right()
                    .some()
            }
            JsonFormatTypes.OBJECT,
            JsonFormatTypes.ANY -> {
                context.compositionFunction
                    .invoke(TypeName.newTypeName(ExtendedScalars.Json.name).build())
                    .right()
                    .some()
            }
            JsonFormatTypes.ARRAY -> {
                innerSchema
                    .asArraySchema()
                    .items
                    .toOption()
                    .mapNotNull { i: ArraySchema.Items -> i.asSingleItems()?.schema }
                    .map { j: JsonSchema ->
                        context
                            .copy(
                                outerSchema = innerSchema,
                                innerSchema = j.some(),
                                compositionLevel = context.compositionLevel + 1,
                                compositionFunction =
                                    context.compositionFunction.compose<
                                        Type<*>, Type<*>, Type<*>
                                    > { t: Type<*> ->
                                        NonNullType.newNonNullType(ListType.newListType(t).build())
                                            .build()
                                    }
                            )
                            .left()
                    }
                    .orElse {
                        innerSchema
                            .asArraySchema()
                            .items
                            .toOption()
                            .mapNotNull { i: ArraySchema.Items -> i.asArrayItems()?.jsonSchemas }
                            .filter { js: Array<JsonSchema> -> js.size == 1 }
                            .map { js: Array<JsonSchema> ->
                                context
                                    .copy(
                                        outerSchema = innerSchema,
                                        innerSchema = js.first().some(),
                                        compositionLevel = context.compositionLevel + 1,
                                        compositionFunction =
                                            context.compositionFunction.compose<
                                                Type<*>, Type<*>, Type<*>
                                            > { t: Type<*> ->
                                                NonNullType.newNonNullType(
                                                        ListType.newListType(t).build()
                                                    )
                                                    .build()
                                            }
                                    )
                                    .left()
                            }
                    }
                    .orElse {
                        innerSchema
                            .asArraySchema()
                            .items
                            .toOption()
                            .mapNotNull { i: ArraySchema.Items -> i.asArrayItems()?.jsonSchemas }
                            .filter { js: Array<JsonSchema> -> js.size > 1 }
                            .map { _: Array<JsonSchema> ->
                                // The array schema has an items schema that spans more than one
                                // element_type
                                context.compositionFunction
                                    .invoke(
                                        NonNullType.newNonNullType(
                                                ListType.newListType(
                                                        TypeName.newTypeName(
                                                                ExtendedScalars.Json.name
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .right()
                            }
                    }
                    .orElse {
                        innerSchema
                            .asArraySchema()
                            .toOption()
                            .filter { a: ArraySchema -> a.items == null }
                            .map { _: ArraySchema ->
                                // The array schema has as null items schema, so "any" type is
                                // accepted
                                context.compositionFunction
                                    .invoke(
                                        NonNullType.newNonNullType(
                                                ListType.newListType(
                                                        TypeName.newTypeName(
                                                                ExtendedScalars.Json.name
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .right()
                            }
                    }
            }
            else -> {
                none<Either<TypeCompositionContext, Type<*>>>()
            }
        }
    }
}
