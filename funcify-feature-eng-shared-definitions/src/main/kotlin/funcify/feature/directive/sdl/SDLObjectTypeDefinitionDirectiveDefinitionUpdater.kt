package funcify.feature.directive.sdl

import graphql.language.ObjectTypeDefinition

@FunctionalInterface
fun interface SDLObjectTypeDefinitionDirectiveDefinitionUpdater {

    fun updateWithDirectiveDefinitionsIfApplicable(
        objectTypeDefinition: ObjectTypeDefinition
    ): ObjectTypeDefinition
}
