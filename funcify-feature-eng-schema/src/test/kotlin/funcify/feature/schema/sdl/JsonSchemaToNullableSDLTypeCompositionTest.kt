package funcify.feature.schema.sdl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema
import com.fasterxml.jackson.module.jsonSchema.types.StringSchema
import com.fasterxml.jackson.module.kotlin.readValue
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.sdl.type.JsonSchemaToNullableSDLTypeComposer
import funcify.feature.tools.json.JsonMapper
import graphql.Scalars
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.Type
import graphql.language.TypeName
import graphql.scalars.ExtendedScalars
import graphql.schema.idl.TypeUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 2023-07-04
 */
class JsonSchemaToNullableSDLTypeCompositionTest {

    private val jsonMapper: JsonMapper = JsonObjectMappingConfiguration.jsonMapper()

    private val objectMapper: ObjectMapper = jsonMapper.jacksonObjectMapper

    @Test
    fun scalarListTest() {
        val arrayTypeSchemaStr: String =
            """
            |{
            | "type": "array",
            | "maxItems": 3,
            | "items": {
            |   "type": "string"
            | }
            |}
            """
                .trimMargin()
        val arraySchema: JsonSchema =
            Assertions.assertDoesNotThrow<JsonSchema> {
                objectMapper.readValue<JsonSchema>(arrayTypeSchemaStr)
            }
        Assertions.assertInstanceOf(ArraySchema::class.java, arraySchema)
        val sdlType: Type<*> =
            Assertions.assertDoesNotThrow<Type<*>> {
                JsonSchemaToNullableSDLTypeComposer.invoke(arraySchema)
            }
        Assertions.assertEquals(
            NonNullType.newNonNullType(
                    ListType.newListType(TypeName.newTypeName(Scalars.GraphQLString.name).build())
                        .build()
                )
                .build()
                .toString(),
            sdlType.toString()
        ) {
            "calculated sdl type does not match expected sdl type"
        }
    }

    @Test
    fun objectListTest() {
        val arrayTypeSchemaStr: String =
            """
            |{
            | "type": "array",
            | "items": {
            |   "type": "object",
            |   "properties": {
            |     "id": {
            |       "type": "integer"
            |     },
            |     "quantity": {
            |       "type": "integer"
            |     },
            |     "shipping_date": {
            |       "type": "string",
            |       "format": "date-time"
            |     }
            |   }
            | }
            |}
            """
                .trimMargin()
        val arraySchema: JsonSchema =
            Assertions.assertDoesNotThrow<JsonSchema> {
                objectMapper.readValue<JsonSchema>(arrayTypeSchemaStr)
            }
        Assertions.assertInstanceOf(ArraySchema::class.java, arraySchema)
        val sdlType: Type<*> =
            Assertions.assertDoesNotThrow<Type<*>> {
                JsonSchemaToNullableSDLTypeComposer.invoke(arraySchema)
            }
        Assertions.assertEquals(
            NonNullType.newNonNullType(
                    ListType.newListType(TypeName.newTypeName(ExtendedScalars.Json.name).build())
                        .build()
                )
                .build()
                .toString(),
            sdlType.toString()
        ) {
            "calculated sdl type does not match expected sdl type"
        }
    }

    @Test
    fun dateTimeTypeTest() {
        val dateTimeScalarSchema: String =
            """
            |{
            | "type": "string",
            | "format": "date-time"
            |}
            """
                .trimMargin()
        val stringSchema: JsonSchema =
            Assertions.assertDoesNotThrow<JsonSchema> {
                objectMapper.readValue<JsonSchema>(dateTimeScalarSchema)
            }
        Assertions.assertInstanceOf(StringSchema::class.java, stringSchema)
        val sdlType: Type<*> =
            Assertions.assertDoesNotThrow<Type<*>> {
                JsonSchemaToNullableSDLTypeComposer.invoke(stringSchema)
            }
        Assertions.assertEquals(
            TypeName.newTypeName(ExtendedScalars.DateTime.name).build().toString(),
            sdlType.toString()
        ) {
            "calculated sdl type does not match expected sdl type"
        }
    }

    @Test
    fun anyOrEmptyElementTypeListTypeTest() {
        val anyTypeListSchema: String =
            """
            |{
            | "type": "array"
            |}
            """
                .trimMargin()
        val arraySchema: JsonSchema =
            Assertions.assertDoesNotThrow<JsonSchema> {
                objectMapper.readValue<JsonSchema>(anyTypeListSchema)
            }
        Assertions.assertInstanceOf(ArraySchema::class.java, arraySchema)
        val sdlType: Type<*> =
            Assertions.assertDoesNotThrow<Type<*>> {
                JsonSchemaToNullableSDLTypeComposer.invoke(arraySchema)
            }
        // Expected: "[JSON]!"
        Assertions.assertEquals(
            TypeUtil.simplePrint(
                NonNullType.newNonNullType(
                        ListType.newListType(
                                TypeName.newTypeName(ExtendedScalars.Json.name).build()
                            )
                            .build()
                    )
                    .build()
            ),
            TypeUtil.simplePrint(sdlType)
        ) {
            "calculated sdl type does not match expected sdl type"
        }
    }
}
