package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.naming.DataSourceSDLDefinitionNamingConventions
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionTypeStrategy
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.FieldDefinition
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 * Strategy: Create "simple" field definition i.e. no directives, arguments, etc. for all vertices
 * with [funcify.feature.schema.index.CompositeSourceAttribute] with:
 * - name based on datasource.key on first available source attribute and following
 * [DataSourceSDLDefinitionNamingConventions]
 * - type based on available [SchematicVertexSDLDefinitionTypeStrategy] (-ies)
 * @author smccarron
 * @created 2022-06-29
 */
class AllSourceAttributesToFieldDefinitionsStrategy(
    private val sdlDefinitionNamingStrategies: List<SchematicVertexSDLDefinitionNamingStrategy>,
    private val sdlDefinitionTypeStrategies: List<SchematicVertexSDLDefinitionTypeStrategy>
) :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy<
        SchematicVertexSDLDefinitionCreationContext<*>>,
    SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger = loggerFor<AllSourceAttributesToFieldDefinitionsStrategy>()
    }

    private val compositeSDLDefinitionNamingStrategy: CompositeSDLDefinitionNamingStrategy =
        CompositeSDLDefinitionNamingStrategy(sdlDefinitionNamingStrategies)

    private val compositeSDLDefinitionTypeStrategy: CompositeSDLDefinitionTypeStrategy =
        CompositeSDLDefinitionTypeStrategy(sdlDefinitionTypeStrategies)

    override val applicableSchematicGraphVertexTypes:
        ImmutableSet<SchematicGraphVertexType> by lazy {
        persistentSetOf(
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX,
            SchematicGraphVertexType.SOURCE_LEAF_VERTEX
        )
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            """determine_sdl_implementation_definition_for_
               |schematic_vertex_in_context: [ 
               |current_vertex.path: ${context.currentVertex.path} 
               |]""".flatten()
        )
        return when (context) {
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                if (context.existingFieldDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    compositeSDLDefinitionNamingStrategy
                        .applyToContext(context)
                        .zip(compositeSDLDefinitionTypeStrategy.applyToContext(context))
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
                    compositeSDLDefinitionNamingStrategy
                        .applyToContext(context)
                        .zip(compositeSDLDefinitionTypeStrategy.applyToContext(context))
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
                    ServiceError.of(
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
