package funcify.feature.datasource.rest.sdl

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import funcify.feature.datasource.rest.naming.RestApiSourceNamingConventions
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import graphql.Scalars
import graphql.language.ListType
import graphql.language.Type
import graphql.language.TypeName
import io.swagger.v3.oas.models.media.Schema

internal object SwaggerSourceAttributeSDLTypeResolver :
    (SwaggerSourceAttribute) -> Option<Type<*>> {

    override fun invoke(sourceAttribute: SwaggerSourceAttribute): Option<Type<*>> {
        return when {
            sourceAttribute.representsPathItemGroup() -> {
                TypeName.newTypeName(
                        RestApiSourceNamingConventions
                            .getPathGroupTypeNamingConventionForPathGroupPathName()
                            .deriveName(
                                sourceAttribute.name.nameSegments.joinToString("_") { ns ->
                                    ns.value
                                }
                            )
                            .qualifiedForm
                    )
                    .build()
                    .some()
            }
            sourceAttribute.representsPathItem() -> {
                TypeName.newTypeName(
                        RestApiSourceNamingConventions
                            .getResponseTypeNamingConventionForResponsePathName()
                            .deriveName(
                                sourceAttribute.name.nameSegments.joinToString("_") { ns ->
                                    ns.value
                                }
                            )
                            .qualifiedForm
                    )
                    .build()
                    .some()
            }
            sourceAttribute.representsResponseBodyProperty() -> {
                // TODO: Introduce recursive function-creator logic similar to what is done for
                // gql-data-sources in sdl definition creation
                sourceAttribute.responseBodyPropertyJsonSchema.flatMap { schema: Schema<*> ->
                    when (JsonFormatTypes.forValue(schema?.type)) {
                        JsonFormatTypes.OBJECT -> {
                            // Assumption is that no name for this type has been provided so the
                            // field name must be used for the typename with some naming convention
                            // adjustment
                            TypeName.newTypeName(
                                    RestApiSourceNamingConventions
                                        .getResponseTypeNamingConventionForResponsePathName()
                                        .deriveName(
                                            sourceAttribute.name.nameSegments.joinToString("_") { ns
                                                ->
                                                ns.value
                                            }
                                        )
                                        .qualifiedForm
                                )
                                .build()
                                .some()
                        }
                        JsonFormatTypes.ARRAY -> {
                            when (JsonFormatTypes.forValue(schema?.items?.type)) {
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
                                    ListType.newListType(
                                            TypeName.newTypeName(Scalars.GraphQLInt.name).build()
                                        )
                                        .build()
                                        .some()
                                }
                                JsonFormatTypes.BOOLEAN -> {
                                    ListType.newListType(
                                            TypeName.newTypeName(Scalars.GraphQLBoolean.name)
                                                .build()
                                        )
                                        .build()
                                        .some()
                                }
                                else -> {
                                    none()
                                }
                            }
                        }
                        JsonFormatTypes.NUMBER -> {
                            TypeName.newTypeName(Scalars.GraphQLFloat.name).build().some()
                        }
                        JsonFormatTypes.STRING -> {
                            TypeName.newTypeName(Scalars.GraphQLString.name).build().some()
                        }
                        JsonFormatTypes.INTEGER -> {
                            TypeName.newTypeName(Scalars.GraphQLInt.name).build().some()
                        }
                        JsonFormatTypes.BOOLEAN -> {
                            TypeName.newTypeName(Scalars.GraphQLBoolean.name).build().some()
                        }
                        else -> {
                            none()
                        }
                    }
                }
            }
            else -> {
                none<Type<*>>()
            }
        }
    }
}
