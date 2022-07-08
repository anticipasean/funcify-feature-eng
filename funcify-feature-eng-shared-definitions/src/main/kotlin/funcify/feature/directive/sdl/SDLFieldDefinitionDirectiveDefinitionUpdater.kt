package funcify.feature.directive.sdl

import graphql.language.FieldDefinition

@FunctionalInterface
fun interface SDLFieldDefinitionDirectiveDefinitionUpdater {

    fun updateWithDirectiveDefinitionsIfApplicable(
        fieldDefinition: FieldDefinition
    ): FieldDefinition
}
