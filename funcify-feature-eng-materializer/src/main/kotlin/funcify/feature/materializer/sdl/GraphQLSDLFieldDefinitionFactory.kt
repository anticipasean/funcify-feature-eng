package funcify.feature.materializer.sdl

import funcify.feature.schema.index.CompositeSourceAttribute
import graphql.language.FieldDefinition

interface GraphQLSDLFieldDefinitionFactory {

    fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeSourceAttribute
    ): FieldDefinition

}
