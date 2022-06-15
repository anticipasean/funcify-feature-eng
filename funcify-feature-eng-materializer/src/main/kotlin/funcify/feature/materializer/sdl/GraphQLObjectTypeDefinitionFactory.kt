package funcify.feature.materializer.sdl

import funcify.feature.schema.index.CompositeContainerType
import graphql.language.ObjectTypeDefinition

interface GraphQLObjectTypeDefinitionFactory {

    fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition

}
