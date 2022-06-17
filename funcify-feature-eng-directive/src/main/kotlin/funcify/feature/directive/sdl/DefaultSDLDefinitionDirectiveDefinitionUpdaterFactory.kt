package funcify.feature.directive.sdl

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.FieldDefinition
import graphql.language.NamedNode
import graphql.language.ObjectTypeDefinition
import org.slf4j.Logger

internal class DefaultSDLDefinitionDirectiveDefinitionUpdaterFactory :
    SDLDefinitionDirectiveDefinitionUpdaterFactory {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSDLDefinitionDirectiveDefinitionUpdaterFactory>()

        internal data class DefaultSDLDefinitionDirectiveDefinitionUpdater(
            private val sdlFieldDefinitionDirectiveDefinitionUpdater:
                SDLFieldDefinitionDirectiveDefinitionUpdater,
            private val sdlObjectTypeDefinitionDirectiveDefinitionUpdater:
                SDLObjectTypeDefinitionDirectiveDefinitionUpdater
        ) : SDLDefinitionDirectiveDefinitionUpdater {

            override fun <N : NamedNode<N>> updateWithDirectiveDefinitionIfApplicable(
                sdlDefinition: N
            ): N {
                logger.debug(
                    """update_with_directive_definition_if_applicable: [ 
                      |sdl_definition: [ name: ${sdlDefinition.name} 
                      |type: ${sdlDefinition::class.qualifiedName}
                      |] ]""".flattenIntoOneLine()
                )
                return when (sdlDefinition) {
                    is FieldDefinition -> {
                        @Suppress("UNCHECKED_CAST") //
                        sdlFieldDefinitionDirectiveDefinitionUpdater
                            .updateWithDirectiveDefinitionsIfApplicable(sdlDefinition) as?
                            N
                    }
                    is ObjectTypeDefinition -> {
                        @Suppress("UNCHECKED_CAST") //
                        sdlObjectTypeDefinitionDirectiveDefinitionUpdater
                            .updateWithDirectiveDefinitionsIfApplicable(sdlDefinition) as?
                            N
                    }
                    else -> {
                        sdlDefinition as? N
                    }
                }
                    ?: sdlDefinition
            }
        }

        internal class DefaultBuilder(
            var sdlFieldDefinitionDirectiveDefinitionUpdater:
                SDLFieldDefinitionDirectiveDefinitionUpdater =
                SDLFieldDefinitionDirectiveDefinitionUpdater { fd: FieldDefinition ->
                    fd
                },
            var sdlObjectTypeDefinitionDirectiveDefinitionUpdater:
                SDLObjectTypeDefinitionDirectiveDefinitionUpdater =
                SDLObjectTypeDefinitionDirectiveDefinitionUpdater { otd: ObjectTypeDefinition ->
                    otd
                }
        ) : SDLDefinitionDirectiveDefinitionUpdaterFactory.Builder {

            override fun fieldDefinitionUpdater(
                sdlFieldDefinitionDirectiveDefinitionUpdater:
                    SDLFieldDefinitionDirectiveDefinitionUpdater
            ): SDLDefinitionDirectiveDefinitionUpdaterFactory.Builder {
                this.sdlFieldDefinitionDirectiveDefinitionUpdater =
                    sdlFieldDefinitionDirectiveDefinitionUpdater
                return this
            }

            override fun objectTypeDefinitionUpdater(
                sdlObjectTypeDefinitionDirectiveDefinitionUpdater:
                    SDLObjectTypeDefinitionDirectiveDefinitionUpdater
            ): SDLDefinitionDirectiveDefinitionUpdaterFactory.Builder {
                this.sdlObjectTypeDefinitionDirectiveDefinitionUpdater =
                    sdlObjectTypeDefinitionDirectiveDefinitionUpdater
                return this
            }

            override fun build(): SDLDefinitionDirectiveDefinitionUpdater {
                return DefaultSDLDefinitionDirectiveDefinitionUpdater(
                    sdlFieldDefinitionDirectiveDefinitionUpdater =
                        sdlFieldDefinitionDirectiveDefinitionUpdater,
                    sdlObjectTypeDefinitionDirectiveDefinitionUpdater =
                        sdlObjectTypeDefinitionDirectiveDefinitionUpdater
                )
            }
        }
    }

    override fun builder(): SDLDefinitionDirectiveDefinitionUpdaterFactory.Builder {
        return DefaultBuilder()
    }
}
