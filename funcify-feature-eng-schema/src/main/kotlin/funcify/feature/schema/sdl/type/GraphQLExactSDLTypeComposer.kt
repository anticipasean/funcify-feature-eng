package funcify.feature.schema.sdl.type

import arrow.core.compose
import arrow.core.identity
import funcify.feature.error.ServiceError
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

object GraphQLExactSDLTypeComposer : (GraphQLType) -> Type<*> {

    override fun invoke(graphQLInputOrOutputType: GraphQLType): Type<*> {
        if (
            graphQLInputOrOutputType !is GraphQLOutputType &&
                graphQLInputOrOutputType !is GraphQLInputType
        ) {
            val message =
                """graphql_input_or_output_type [ type: %s ] 
                    |currently not handled by function %s"""
                    .format(
                        graphQLInputOrOutputType::class.simpleName,
                        GraphQLExactSDLTypeComposer::class.simpleName
                    )
                    .flatten()
            throw ServiceError.of(message)
        }
        return resolveType(graphQLInputOrOutputType, ::identity)
    }

    private tailrec fun resolveType(
        graphQLInputOrOutputType: GraphQLType,
        typeComposer: (Type<*>) -> Type<*>
    ): Type<*> {
        return when (graphQLInputOrOutputType) {
            is GraphQLNonNull -> {
                resolveType(
                    graphQLInputOrOutputType.wrappedType,
                    typeComposer.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                        NonNullType.newNonNullType().type(t).build()
                    }
                )
            }
            is GraphQLList -> {
                resolveType(
                    graphQLInputOrOutputType.wrappedType,
                    typeComposer.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                        ListType.newListType().type(t).build()
                    }
                )
            }
            is GraphQLNamedType -> {
                typeComposer.invoke(
                    TypeName.newTypeName().name(graphQLInputOrOutputType.name).build()
                )
            }
            else -> {
                throw ServiceError.of(
                    "nested type not of [ type: %s ] [ actual type: %s ]",
                    GraphQLNamedType::class.qualifiedName,
                    graphQLInputOrOutputType::class.qualifiedName
                )
            }
        }
    }
}
