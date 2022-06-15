package funcify.feature.materializer.sdl

import funcify.feature.schema.index.CompositeAttribute
import graphql.language.FieldDefinition

interface GraphQLSDLFieldDefinitionFactory {

    fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition

}
