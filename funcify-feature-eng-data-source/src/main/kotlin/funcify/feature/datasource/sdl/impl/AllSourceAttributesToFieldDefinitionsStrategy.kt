package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.naming.SchemaDefinitionLanguageNamingConventions
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionTypeStrategy
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import graphql.language.FieldDefinition
import graphql.language.Type
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Strategy: Create "simple" field definition i.e. no directives, arguments, etc. for all vertices
 * with [funcify.feature.schema.index.CompositeSourceAttribute] with:
 * - name based on datasource.key on first available source attribute and following
 * [SchemaDefinitionLanguageNamingConventions]
 * - type based on available [SchematicVertexSDLDefinitionTypeStrategy] (-ies)
 * @author smccarron
 * @created 2022-06-29
 */
class AllSourceAttributesToFieldDefinitionsStrategy(
    private val sdlDefinitionNamingStrategies: List<SchematicVertexSDLDefinitionNamingStrategy>,
    private val sdlDefinitionTypeStrategies: List<SchematicVertexSDLDefinitionTypeStrategy>
) :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy,
    SchematicVertexSDLDefinitionImplementationStrategy {

    private val composedNamingStrategy: SchematicVertexSDLDefinitionNamingStrategy by lazy {
        val emptyStrategiesFailure: Try<String> =
            Try.failure<String>(
                DataSourceException(
                    DataSourceErrorResponse.STRATEGY_MISSING,
                    "no naming strategy was provided"
                )
            )
        SchematicVertexSDLDefinitionNamingStrategy { ctx ->
            sdlDefinitionNamingStrategies.asSequence().sorted().fold(emptyStrategiesFailure) {
                namingAttempt: Try<String>,
                strategy: SchematicVertexSDLDefinitionNamingStrategy ->
                if (namingAttempt.isSuccess()) {
                    namingAttempt
                } else {
                    strategy.determineNameForSDLDefinitionForSchematicVertexInContext(ctx)
                }
            }
        }
    }

    // TODO: Move this composition logic out into separate "composite" type when API more stable
    private val composedTypeSelectionStrategy: SchematicVertexSDLDefinitionTypeStrategy by lazy {
        val emptyStrategiesFailure: Try<Type<*>> =
            Try.failure<Type<*>>(
                DataSourceException(
                    DataSourceErrorResponse.STRATEGY_MISSING,
                    "no type resolving strategy was provided"
                )
            )
        SchematicVertexSDLDefinitionTypeStrategy { ctx ->
            sdlDefinitionTypeStrategies.asSequence().sorted().fold(emptyStrategiesFailure) {
                typeResolutionAttempt: Try<Type<*>>,
                strategy: SchematicVertexSDLDefinitionTypeStrategy ->
                if (typeResolutionAttempt.isSuccess()) {
                    typeResolutionAttempt
                } else {
                    strategy.determineSDLTypeForSchematicVertexInContext(ctx)
                }
            }
        }
    }

    override val applicableSchematicGraphVertexTypes:
        ImmutableSet<SchematicGraphVertexType> by lazy {
        persistentSetOf(
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX,
            SchematicGraphVertexType.SOURCE_LEAF_VERTEX
        )
    }

    override fun determineSDLImplementationDefinitionForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        // TODO: Add logging statements once API stable
        return when (context) {
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                if (context.existingFieldDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    composedNamingStrategy
                        .determineNameForSDLDefinitionForSchematicVertexInContext(context)
                        .zip(
                            composedTypeSelectionStrategy
                                .determineSDLTypeForSchematicVertexInContext(context)
                        )
                        .map { (name, sdlType) ->
                            FieldDefinition.newFieldDefinition()
                                .name(name)
                                //                                .description(
                                //                                    Description(
                                //                                        """data_source: [ name:
                                // ${dsKey.name} ]
                                //                                        |[
                                // container_attribute_name: ${sourceAttr.name} ]
                                //                                        |""".flattenIntoOneLine(),
                                //                                        SourceLocation.EMPTY,
                                //                                        false
                                //                                    )
                                //                                )
                                .type(sdlType)
                                .build()
                        }
                        .map { fieldDef ->
                            context.update {
                                addSDLDefinitionForSchematicPath(context.path, fieldDef)
                            }
                        }
                }
            }
            is SourceLeafVertexSDLDefinitionCreationContext -> {
                if (context.existingFieldDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    composedNamingStrategy
                        .determineNameForSDLDefinitionForSchematicVertexInContext(context)
                        .zip(
                            composedTypeSelectionStrategy
                                .determineSDLTypeForSchematicVertexInContext(context)
                        )
                        .map { (name, sdlType) ->
                            FieldDefinition.newFieldDefinition()
                                .name(name)
                                //                                .description(
                                //                                    Description(
                                //                                        """data_source: [ name:
                                // ${dsKey.name} ]
                                //                                        |[
                                // container_attribute_name: ${sourceAttr.name} ]
                                //                                        |""".flattenIntoOneLine(),
                                //                                        SourceLocation.EMPTY,
                                //                                        false
                                //                                    )
                                //                                )
                                .type(sdlType)
                                .build()
                        }
                        .map { fieldDef ->
                            context.update {
                                addSDLDefinitionForSchematicPath(context.path, fieldDef)
                            }
                        }
                }
            }
            else -> {
                val clsNameInSnakeFormat =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                        AllSourceAttributesToFieldDefinitionsStrategy::class.simpleName ?: ""
                    )
                val applicableTypesAsString =
                    applicableSchematicGraphVertexTypes.joinToString(
                        ", ",
                        "{ ",
                        " }",
                        transform = SchematicGraphVertexType::toString
                    )
                Try.failure(
                    DataSourceException(
                        DataSourceErrorResponse.STRATEGY_INCORRECTLY_APPLIED,
                        """$clsNameInSnakeFormat should not be applied to context 
                            |[ expected: context for types $applicableTypesAsString, 
                            |actual: context for ${context.currentGraphVertexType} ]
                            |"""
                    )
                )
            }
        }
    }
}
