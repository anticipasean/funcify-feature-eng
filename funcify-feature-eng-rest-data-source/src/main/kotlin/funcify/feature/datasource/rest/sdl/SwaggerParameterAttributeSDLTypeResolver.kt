package funcify.feature.datasource.rest.sdl

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import funcify.feature.datasource.rest.naming.RestApiSourceNamingConventions
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import graphql.Scalars
import graphql.language.ListType
import graphql.language.Type
import graphql.language.TypeName

object SwaggerParameterAttributeSDLTypeResolver : (SwaggerParameterAttribute) -> Option<Type<*>> {

    override fun invoke(parameterAttribute: SwaggerParameterAttribute): Option<Type<*>> {
        return when (JsonFormatTypes.forValue(parameterAttribute.jsonSchema?.type)) {
            JsonFormatTypes.STRING -> {
                TypeName.newTypeName(Scalars.GraphQLString.name).build().some()
            }
            JsonFormatTypes.NUMBER -> {
                TypeName.newTypeName(Scalars.GraphQLFloat.name).build().some()
            }
            JsonFormatTypes.INTEGER -> {
                TypeName.newTypeName(Scalars.GraphQLInt.name).build().some()
            }
            JsonFormatTypes.BOOLEAN -> {
                TypeName.newTypeName(Scalars.GraphQLBoolean.name).build().some()
            }
            JsonFormatTypes.OBJECT -> {
                TypeName.newTypeName(
                        RestApiSourceNamingConventions
                            .getRequestTypeNamingConventionForRequestPathName()
                            .deriveName(parameterAttribute.jsonPropertyName)
                            .qualifiedForm
                    )
                    .build()
                    .some()
            }
            JsonFormatTypes.ARRAY -> {
                when (JsonFormatTypes.forValue(parameterAttribute.jsonSchema?.items?.type)) {
                    JsonFormatTypes.STRING -> {
                        ListType.newListType(
                                TypeName.newTypeName(Scalars.GraphQLString.name).build()
                            )
                            .build()
                            .some()
                    }
                    JsonFormatTypes.NUMBER -> {
                        ListType.newListType(
                                TypeName.newTypeName(Scalars.GraphQLFloat.name).build()
                            )
                            .build()
                            .some()
                    }
                    JsonFormatTypes.INTEGER -> {
                        ListType.newListType(TypeName.newTypeName(Scalars.GraphQLInt.name).build())
                            .build()
                            .some()
                    }
                    JsonFormatTypes.BOOLEAN -> {
                        ListType.newListType(
                                TypeName.newTypeName(Scalars.GraphQLBoolean.name).build()
                            )
                            .build()
                            .some()
                    }
                    JsonFormatTypes.OBJECT -> {
                        ListType.newListType(
                                TypeName.newTypeName(
                                        RestApiSourceNamingConventions
                                            .getRequestTypeNamingConventionForRequestPathName()
                                            .deriveName(parameterAttribute.jsonPropertyName)
                                            .qualifiedForm
                                    )
                                    .build()
                            )
                            .build()
                            .some()
                    }
                    else -> none()
                }
            }
            else -> {
                none()
            }
        }
    }
}
