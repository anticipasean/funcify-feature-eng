package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.ObjectTypeDefinition
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 * Strategy: Create [ObjectTypeDefinition]s for all source index container types rather than more
 * nuanced strategy leveraging:
 * - [graphql.language.InterfaceTypeDefinition]s
 * - [graphql.language.UnionTypeDefinition]s
 * - [graphql.language.InterfaceTypeExtensionDefinition]s
 * - [graphql.language.UnionTypeExtensionDefinition]s
 * - [graphql.language.ObjectTypeExtensionDefinition]s
 * @author smccarron
 * @created 2022-06-27
 */
class AllSourceContainerTypesToObjectTypeDefinitionsStrategy(
    private val sdlDefinitionNamingStrategies: List<SchematicVertexSDLDefinitionNamingStrategy>
) :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy,
    SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger =
            loggerFor<AllSourceContainerTypesToObjectTypeDefinitionsStrategy>()
    }

    private val compositeSDLDefinitionNamingStrategy: CompositeSDLDefinitionNamingStrategy =
        CompositeSDLDefinitionNamingStrategy(sdlDefinitionNamingStrategies)

    override val applicableSchematicGraphVertexTypes:
        ImmutableSet<SchematicGraphVertexType> by lazy {
        persistentSetOf(
            SchematicGraphVertexType.SOURCE_ROOT_VERTEX,
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX
        )
    }

    override fun determineSDLImplementationDefinitionForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug(
            """determine_sdl_implementation_definition_for_
               |schematic_vertex_in_context: [ 
               |current_vertex.path: ${context.currentVertex.path} 
               |]""".flattenIntoOneLine()
        )
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                if (context.existingObjectTypeDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    compositeSDLDefinitionNamingStrategy
                        .determineNameForSDLDefinitionForSchematicVertexInContext(context)
                        .map { name ->
                            ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(name)
                                //                                .description(
                                //                                    Description(
                                //                                        "data_source: [ name:
                                // ${entry.key.name} ] [ container_type_name: ${entry.value.name}
                                // ]",
                                //                                        SourceLocation.EMPTY,
                                //                                        false
                                //                                    )
                                //                                )
                                .build()
                        }
                        .map { objTypeDef ->
                            context.update {
                                addSDLDefinitionForSchematicPath(context.path, objTypeDef)
                            }
                        }
                }
            }
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                if (context.existingObjectTypeDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    compositeSDLDefinitionNamingStrategy
                        .determineNameForSDLDefinitionForSchematicVertexInContext(context)
                        .map { name ->
                            ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(name)
                                //                                .description(
                                //                                    Description(
                                //                                        "data_source: [ name:
                                // ${entry.key.name} ] [ container_type_name: ${entry.value.name}
                                // ]",
                                //                                        SourceLocation.EMPTY,
                                //                                        false
                                //                                    )
                                //                                )
                                .build()
                        }
                        .map { objTypeDef ->
                            context.update {
                                addSDLDefinitionForSchematicPath(context.path, objTypeDef)
                            }
                        }
                }
            }
            else -> {
                val clsNameInSnakeFormat =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                        AllSourceContainerTypesToObjectTypeDefinitionsStrategy::class.simpleName
                            ?: ""
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
