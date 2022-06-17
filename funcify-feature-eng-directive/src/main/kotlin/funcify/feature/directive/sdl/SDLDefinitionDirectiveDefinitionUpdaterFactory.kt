package funcify.feature.directive.sdl

interface SDLDefinitionDirectiveDefinitionUpdaterFactory {

    fun builder(): Builder

    interface Builder {

        fun fieldDefinitionUpdater(
            sdlFieldDefinitionDirectiveDefinitionUpdater:
                SDLFieldDefinitionDirectiveDefinitionUpdater
        ): Builder

        fun objectTypeDefinitionUpdater(
            sdlObjectTypeDefinitionDirectiveDefinitionUpdater:
                SDLObjectTypeDefinitionDirectiveDefinitionUpdater
        ): Builder

        fun build(): SDLDefinitionDirectiveDefinitionUpdater
    }
}
