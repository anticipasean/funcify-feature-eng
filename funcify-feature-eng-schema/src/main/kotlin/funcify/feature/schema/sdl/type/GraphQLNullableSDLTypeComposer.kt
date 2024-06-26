package funcify.feature.schema.sdl.type

import arrow.core.Either
import arrow.core.Option
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType

// TODO: Create tests for composing nullable sdl_types
object GraphQLNullableSDLTypeComposer : (GraphQLType) -> Type<*> {

    private data class TypeCompositionContext(
        val outerGraphQLType: GraphQLType,
        val innerGraphQLType: Option<GraphQLType> = none(),
        val compositionLevel: Int = 0,
        val compositionFunction: (Type<*>) -> Type<*> = ::identity
    )

    private val typeComposingRecursiveFunction:
                (TypeCompositionContext) -> Option<Either<TypeCompositionContext, Type<*>>> by lazy {
        { context: TypeCompositionContext ->
            val innerGraphQLType: GraphQLType =
                context.innerGraphQLType.orNull() ?: context.outerGraphQLType
            when {
                /** Make base type non-nullable if source has it specified as such */
                context.compositionLevel == 0 && innerGraphQLType is GraphQLNonNull -> {
                    context.compositionFunction
                        .compose<Type<*>, Type<*>, Type<*>> { t: Type<*> -> t }
                        .left()
                        .mapLeft { compFunc ->
                            context.copy(
                                outerGraphQLType = innerGraphQLType,
                                innerGraphQLType = innerGraphQLType.wrappedType.some(),
                                compositionLevel = context.compositionLevel + 1,
                                compositionFunction = compFunc
                            )
                        }
                        .some()
                }
                /** Make List types non-nullable if they aren't specified as such */
                context.compositionLevel == 0 && innerGraphQLType is GraphQLList -> {
                    context.compositionFunction
                        .compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                            NonNullType.newNonNullType()
                                .type(ListType.newListType(t).build())
                                .build()
                        }
                        .left()
                        .mapLeft { compFunc ->
                            context.copy(
                                outerGraphQLType = innerGraphQLType,
                                innerGraphQLType = innerGraphQLType.wrappedType.some(),
                                compositionLevel = context.compositionLevel + 1,
                                compositionFunction = compFunc
                            )
                        }
                        .some()
                }
                context.compositionLevel == 0 && innerGraphQLType is GraphQLNamedType -> {
                    context.compositionFunction
                        .invoke(TypeName.newTypeName(innerGraphQLType.name).build())
                        .right()
                        .some()
                }
                context.compositionLevel > 0 && innerGraphQLType is GraphQLList -> {
                    context.compositionFunction
                        .compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                            ListType.newListType(t).build()
                        }
                        .left()
                        .mapLeft { compFunc ->
                            context.copy(
                                outerGraphQLType = innerGraphQLType,
                                innerGraphQLType = innerGraphQLType.wrappedType.some(),
                                compositionLevel = context.compositionLevel + 1,
                                compositionFunction = compFunc
                            )
                        }
                        .some()
                }
                /** Make List elements non-nullable if they aren't already */
                context.compositionLevel > 0 &&
                    context.outerGraphQLType is GraphQLList &&
                    innerGraphQLType is GraphQLNamedType -> {
                    context.compositionFunction
                        .invoke(
                            NonNullType.newNonNullType(
                                    TypeName.newTypeName(innerGraphQLType.name).build()
                                )
                                .build()
                        )
                        .right()
                        .some()
                }
                /**
                 * Keep named types nullable if their source specifies them as such provided that
                 * they are not an element type in a list
                 */
                context.compositionLevel > 0 && innerGraphQLType is GraphQLNamedType -> {
                    context.compositionFunction
                        .invoke(TypeName.newTypeName(innerGraphQLType.name).build())
                        .right()
                        .some()
                }
                else -> {
                    none()
                }
            }
        }
    }

    override fun invoke(graphQLInputOrOutputType: GraphQLType): Type<*> {
        if (
            graphQLInputOrOutputType !is GraphQLOutputType &&
                graphQLInputOrOutputType !is GraphQLInputType
        ) {
            val message =
                """the graphql_type passed in as input is 
                   |neither an input or output type 
                   |so an SDL Type<*> instance cannot be determined: 
                   |[ actual: ${graphQLInputOrOutputType::class.qualifiedName} 
                   |]"""
                    .flatten()
            throw ServiceError.of(message)
        }
        return TypeCompositionContext(outerGraphQLType = graphQLInputOrOutputType)
            .some()
            .recurse(typeComposingRecursiveFunction)
            .fold(
                { ->
                    throw unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
                        graphQLInputOrOutputType
                    )
                },
                { t: Type<*> -> t }
            )
    }

    private fun unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
        graphQLInputOrOutputType: GraphQLType
    ): ServiceError {
        val inputOrOutputType: String =
            if (graphQLInputOrOutputType is GraphQLInputType) {
                "input_type"
            } else {
                "output_type"
            }
        return ServiceError.of(
            """graphql_field_definition.${inputOrOutputType} 
                |[ type.to_string: 
                |$graphQLInputOrOutputType ] 
                |does not have name for use in SDL type creation"""
                .flatten()
        )
    }
}
