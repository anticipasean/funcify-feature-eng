package funcify.feature.schema.sdl

import funcify.feature.directive.MaterializationDirectiveRegistry
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.DirectiveDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.SDLNamedDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-23
 */
class UnsupportedDirectivesTypeDefinitionRegistryFilter(
    private val materializationDirectiveRegistry: MaterializationDirectiveRegistry
) : TypeDefinitionRegistryFilter {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
        private val logger: Logger = loggerFor<UnsupportedDirectivesTypeDefinitionRegistryFilter>()
    }

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "filter: [ type_definition_registry.directive_definitions.name: {} ]",
                typeDefinitionRegistry.directiveDefinitions.keys.asSequence().joinToString(", ")
            )
        }
        val supportedDirectiveDefinitionsSet: Set<DirectiveDefinition> =
            materializationDirectiveRegistry.getAllDirectiveDefinitions().toSet()
        val supportedDirectiveDefinitionNamesSet: Set<String> =
            supportedDirectiveDefinitionsSet.asSequence().map(DirectiveDefinition::getName).toSet()
        return when {
            typeDefinitionRegistry.directiveDefinitions.any { (name: String, _: DirectiveDefinition)
                ->
                name !in supportedDirectiveDefinitionNamesSet
            } -> {
                Result.failure<TypeDefinitionRegistry>(
                    ServiceError.of(
                        "unsupported directive_definitions found in type_definition_registry: [ names: %s ]",
                        typeDefinitionRegistry.directiveDefinitions.keys
                            .asSequence()
                            .filterNot { n: String -> n !in supportedDirectiveDefinitionNamesSet }
                            .joinToString(", ")
                    )
                )
            }
            typeDefinitionRegistry.directiveDefinitions.any { (name: String, _: DirectiveDefinition)
                ->
                name in supportedDirectiveDefinitionNamesSet
            } -> {
                sequenceOf<Sequence<SDLNamedDefinition<*>>>(
                        typeDefinitionRegistry.directiveDefinitions.values.asSequence(),
                        typeDefinitionRegistry
                            .getTypesMap(InputObjectTypeDefinition::class.java)
                            .values
                            .asSequence(),
                        typeDefinitionRegistry
                            .getTypesMap(EnumTypeDefinition::class.java)
                            .values
                            .asSequence()
                    )
                    .flatten()
                    .filter { sd: SDLNamedDefinition<*> ->
                        materializationDirectiveRegistry.getDirectiveDefinitionWithName(sd.name) !=
                            null ||
                            materializationDirectiveRegistry
                                .getReferencedEnumTypeDefinitionWithName(sd.name) != null ||
                            materializationDirectiveRegistry
                                .getReferencedInputObjectTypeDefinitionWithName(sd.name) != null
                    }
                    .fold(Try.success(typeDefinitionRegistry)) {
                        tdrAttempt: Try<TypeDefinitionRegistry>,
                        sd: SDLNamedDefinition<*> ->
                        tdrAttempt.flatMap { tdr: TypeDefinitionRegistry ->
                            Try.attempt {
                                    tdr.remove(sd)
                                    tdr
                                }
                                .mapFailure { t: Throwable ->
                                    ServiceError.builder()
                                        .message(
                                            """error occurred when removing sdl_definition related to a 
                                            |materialization_directive
                                            |[ name: %s ] 
                                            |from type_definition_registry"""
                                                .flatten()
                                                .format(sd.name)
                                        )
                                        .cause(t)
                                        .build()
                                }
                        }
                    }
                    .toResult()
            }
            else -> {
                Result.success(typeDefinitionRegistry)
            }
        }
    }
}
