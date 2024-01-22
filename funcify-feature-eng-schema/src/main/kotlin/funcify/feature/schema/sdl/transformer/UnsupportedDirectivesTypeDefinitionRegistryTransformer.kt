package funcify.feature.schema.sdl.transformer

import funcify.feature.directive.MaterializationDirectiveRegistry
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.foldIntoTry
import graphql.language.DirectiveDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.SDLNamedDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger
import org.springframework.core.Ordered
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-23
 */
class UnsupportedDirectivesTypeDefinitionRegistryTransformer(
    private val materializationDirectiveRegistry: MaterializationDirectiveRegistry
) : OrderedTypeDefinitionRegistryTransformer {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
        private val logger: Logger =
            loggerFor<UnsupportedDirectivesTypeDefinitionRegistryTransformer>()
    }

    override fun getOrder(): Int {
        return Ordered.LOWEST_PRECEDENCE
    }

    override fun transform(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Mono<out TypeDefinitionRegistry> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "transform: [ type_definition_registry.directive_definitions.name: {} ]",
                typeDefinitionRegistry.directiveDefinitions.keys.asSequence().joinToString(", ")
            )
        }
        val supportedDirectiveDefinitionsSet: Set<DirectiveDefinition> =
            materializationDirectiveRegistry.getAllDirectiveDefinitions().toSet()
        val supportedDirectiveDefinitionNamesSet: Set<String> =
            supportedDirectiveDefinitionsSet.asSequence().map(DirectiveDefinition::getName).toSet()
        return when {
                typeDefinitionRegistry.directiveDefinitions.any {
                    (name: String, _: DirectiveDefinition) ->
                    name !in supportedDirectiveDefinitionNamesSet
                } -> {
                    Try.failure<TypeDefinitionRegistry>(
                        ServiceError.of(
                            "unsupported directive_definitions found in type_definition_registry: [ names: %s ]",
                            typeDefinitionRegistry.directiveDefinitions.keys
                                .asSequence()
                                .filterNot { n: String ->
                                    n !in supportedDirectiveDefinitionNamesSet
                                }
                                .joinToString(", ")
                        )
                    )
                }
                typeDefinitionRegistry.directiveDefinitions.any {
                    (name: String, _: DirectiveDefinition) ->
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
                            materializationDirectiveRegistry.getDirectiveDefinitionWithName(
                                sd.name
                            ) != null ||
                                materializationDirectiveRegistry
                                    .getReferencedEnumTypeDefinitionWithName(sd.name) != null ||
                                materializationDirectiveRegistry
                                    .getReferencedInputObjectTypeDefinitionWithName(sd.name) != null
                        }
                        .foldIntoTry(typeDefinitionRegistry) {
                            tdr: TypeDefinitionRegistry,
                            sd: SDLNamedDefinition<*> ->
                            try {
                                tdr.apply { remove(sd) }
                            } catch (e: Exception) {
                                throw ServiceError.builder()
                                    .message(
                                        """error occurred when removing sdl_definition related to a 
                                    |materialization_directive
                                    |[ name: %s ] 
                                    |from type_definition_registry"""
                                            .flatten()
                                            .format(sd.name)
                                    )
                                    .cause(e)
                                    .build()
                            }
                        }
                }
                else -> {
                    Try.success(typeDefinitionRegistry)
                }
            }
            .peek(
                { _: TypeDefinitionRegistry -> logger.debug("transform: [ status: successful ]") },
                { _: Throwable -> logger.error("transform: [ status: failed ]") }
            )
            .toMono()
    }
}
