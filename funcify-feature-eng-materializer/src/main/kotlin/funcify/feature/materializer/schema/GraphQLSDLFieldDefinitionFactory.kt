package funcify.feature.materializer.schema

import funcify.feature.schema.index.CompositeAttribute
import graphql.language.FieldDefinition

interface GraphQLSDLFieldDefinitionFactory {

    fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition

}
