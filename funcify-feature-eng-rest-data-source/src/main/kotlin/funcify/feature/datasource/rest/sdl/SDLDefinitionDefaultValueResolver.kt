package funcify.feature.datasource.rest.sdl

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import graphql.language.ArrayValue
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.NullValue
import graphql.language.Type
import graphql.language.TypeName
import graphql.language.Value

internal object SDLDefinitionDefaultValueResolver : (Type<*>) -> Option<Value<*>> {

    override fun invoke(sdlType: Type<*>): Option<Value<*>> {
        return when (sdlType) {
            is NonNullType -> {
                when (sdlType.type) {
                    is ListType -> {
                        ArrayValue.newArrayValue().build().some()
                    }
                    else -> {
                        none()
                    }
                }
            }
            is ListType -> {
                ArrayValue.newArrayValue().build().some()
            }
            is TypeName -> {
                NullValue.of().some()
            }
            else -> {
                NullValue.of().some()
            }
        }
    }
}
