package funcify.feature.directive.sdl

import graphql.language.NamedNode

interface SDLDefinitionDirectiveDefinitionUpdater {

    fun <N : NamedNode<N>> updateWithDirectiveDefinitionIfApplicable(sdlDefinition: N): N

}
