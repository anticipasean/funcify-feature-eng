package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.toPersistentSet

/**
 * Strategy: Take other naming strategies as input, sort them based on priority, apply each until
 * one naming attempt is successful, and return that Success<String> instance
 * @author smccarron
 * @created 2022-06-30
 */
class CompositeSDLDefinitionNamingStrategy(
    private val sdlDefinitionNamingStrategies: List<SchematicVertexSDLDefinitionNamingStrategy>
) : SchematicVertexSDLDefinitionNamingStrategy {

    private val composedNamingStrategy: SchematicVertexSDLDefinitionNamingStrategy by lazy {
        SchematicVertexSDLDefinitionNamingStrategy { ctx ->
            sdlDefinitionNamingStrategies.asSequence().sorted().fold(
                    createEmptyOrInapplicableFailureForContext(ctx)
                ) { namingAttempt: Try<String>, strategy: SchematicVertexSDLDefinitionNamingStrategy
                ->
                /*
                 * earlier naming attempt was successful, so keep that name
                 */
                when {
                    namingAttempt.isSuccess() -> {
                        namingAttempt /*
                                       * test whether strategy is graph_vertex_type_based and
                                       * if so, check whether strategy is applicable to given context's vertex type
                                       * before application to the context
                                       */
                    }
                    strategy.canBeAppliedToContext(ctx) -> {
                        strategy.applyToContext(ctx)
                    }
                    else -> {
                        /*
                         * strategy could not be applied, so prior error state holds
                         */
                        namingAttempt
                    }
                }
            }
        }
    }

    private fun createEmptyOrInapplicableFailureForContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<String> {
        return Try.failure<String>(
            DataSourceException(
                DataSourceErrorResponse.STRATEGY_MISSING,
                createEmptyOrInapplicableStrategiesMessageForContext(context)
            )
        )
    }

    private fun createEmptyOrInapplicableStrategiesMessageForContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): String {
        return if (sdlDefinitionNamingStrategies.isEmpty()) {
            """no sdl_definition_naming_strategies were provided"""
        } else {
            val supportedGraphVertexTypesSetString: String =
                sdlDefinitionNamingStrategies
                    .asSequence()
                    .map { strat ->
                        /*
                         * extract applicable graph vertex types from each strategy that is type-based
                         */
                        if (strat is SchematicGraphVertexTypeBasedSDLDefinitionStrategy<*>) {
                            strat.applicableSchematicGraphVertexTypes.asIterable()
                        } else {
                            /*
                             * if strategy is not type-based, assume all vertex types are supported
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
            """none of the sdl_definition_naming_strategies provided were applicable: [ 
               |expected_strategy_supporting: { ${context.currentGraphVertexType} }, 
               |actual_strategies_supporting: $supportedGraphVertexTypesSetString 
               |]
               |""".flattenIntoOneLine()
        }
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<String> {
        return composedNamingStrategy.applyToContext(context)
    }
}
