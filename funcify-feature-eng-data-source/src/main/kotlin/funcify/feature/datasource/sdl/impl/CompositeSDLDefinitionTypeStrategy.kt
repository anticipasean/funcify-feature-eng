package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionTypeStrategy
import funcify.feature.error.ServiceError
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Type
import kotlinx.collections.immutable.toPersistentSet

/**
 * Strategy: Take other type strategies as input, sort them based on priority, apply each until one
 * type resolution attempt is successful, and return that Success<Type<*>> instance
 * @author smccarron
 * @created 2022-06-30
 */
class CompositeSDLDefinitionTypeStrategy(
    private val sdlDefinitionTypeStrategies: List<SchematicVertexSDLDefinitionTypeStrategy>
) : SchematicVertexSDLDefinitionTypeStrategy {

    private val composedTypeDeterminationStrategy:
        SchematicVertexSDLDefinitionTypeStrategy by lazy {
        SchematicVertexSDLDefinitionTypeStrategy { ctx ->
            sdlDefinitionTypeStrategies.asSequence().sorted().fold(
                createEmptyOrInapplicableFailureForContext(ctx)
            ) {
                typeResolutionAttempt: Try<Type<*>>,
                strategy: SchematicVertexSDLDefinitionTypeStrategy ->
                /*
                 * earlier type resolution attempt was successful, so keep that name
                 */
                when {
                    typeResolutionAttempt.isSuccess() -> {
                        typeResolutionAttempt
                        /*
                         * test whether strategy is graph_vertex_type_based and
                         * if so, check whether strategy is applicable to given context's vertex type
                         * before application to the context
                         */
                    }
                    strategy.canBeAppliedToContext(context = ctx) -> {
                        strategy.applyToContext(ctx)
                    }
                    else -> {
                        /*
                         * strategy could not be applied, so prior error state holds
                         */
                        typeResolutionAttempt
                    }
                }
            }
        }
    }

    private fun createEmptyOrInapplicableFailureForContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<Type<*>> {
        return Try.failure<Type<*>>(
            ServiceError.of(createEmptyOrInapplicableStrategiesMessageForContext(context))
        )
    }

    private fun createEmptyOrInapplicableStrategiesMessageForContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): String {
        return if (sdlDefinitionTypeStrategies.isEmpty()) {
            """no sdl_definition_type_strategies were provided"""
        } else {
            val supportedGraphVertexTypesSetString: String =
                sdlDefinitionTypeStrategies
                    .asSequence()
                    .map { strat ->
                        /*
                         * extract applicable graph vertex types from each strategy that is type-based
                         */
                        if (strat is SchematicGraphVertexTypeBasedSDLDefinitionStrategy<*>) {
                            strat.applicableSchematicGraphVertexTypes.asIterable()
                        } else {
                            /*
                             * if strategy is not type-based, assume all graph vertex types are supported
                             */
                            SchematicGraphVertexType.values().asIterable()
                        }
                    }
                    .flatMap { sgvts -> sgvts.asSequence() }
                    .toPersistentSet()
                    .asSequence()
                    .sorted()
                    .map { sgvt -> sgvt.name }
                    .joinToString(", ", "{ ", " }")
            """none of the sdl_definition_type_strategies provided were applicable: [ 
               |expected_strategy_supporting: { ${context.currentGraphVertexType} }, 
               |actual_strategies_supporting: $supportedGraphVertexTypesSetString 
               |]
               |""".flatten()
        }
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<Type<*>> {
        return composedTypeDeterminationStrategy.applyToContext(context)
    }
}
