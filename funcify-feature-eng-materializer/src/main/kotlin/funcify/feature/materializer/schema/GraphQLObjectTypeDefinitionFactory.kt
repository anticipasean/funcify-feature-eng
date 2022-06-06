package funcify.feature.materializer.schema

import funcify.feature.schema.index.CompositeContainerType
import graphql.language.ObjectTypeDefinition

interface GraphQLObjectTypeDefinitionFactory {

    fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition

}
