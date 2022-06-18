package funcify.feature.materializer.sdl

import funcify.feature.schema.index.CompositeSourceContainerType
import graphql.language.ObjectTypeDefinition

interface GraphQLObjectTypeDefinitionFactory {

    fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeSourceContainerType
    ): ObjectTypeDefinition

}
