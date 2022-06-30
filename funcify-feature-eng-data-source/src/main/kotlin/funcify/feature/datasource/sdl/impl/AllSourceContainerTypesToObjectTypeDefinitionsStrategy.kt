package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.naming.SchemaDefinitionLanguageNamingConventions
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

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
class AllSourceContainerTypesToObjectTypeDefinitionsStrategy :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy,
    SchematicVertexSDLDefinitionImplementationStrategy {

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
        // TODO: Add logging statements once API stable
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                if (context.existingObjectTypeDefinition.isDefined()) {
                    Try.success(context)
                } else {
                    Try.attempt(
                            context
                                .compositeSourceContainerType
                                .getSourceContainerTypeByDataSource()
                                .asSequence()::first
                        )
                        .mapFailure { t ->
                            DataSourceException(
                                DataSourceErrorResponse.DATASOURCE_SCHEMA_INTEGRITY_VIOLATION,
                                """composite_source_container_type must have at 
                                   |least one datasource defined: [ name: 
                                   |${context.compositeSourceContainerType.conventionalName} 
                                   |]
                                   |""".flattenIntoOneLine()
                            )
                        }
                        .map { entry ->
                            ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(
                                    SchemaDefinitionLanguageNamingConventions
                                        .OBJECT_TYPE_NAMING_CONVENTION
                                        .deriveName(entry.value)
                                        .toString()
                                )
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
                    Try.attempt(
                            context
                                .compositeSourceContainerType
                                .getSourceContainerTypeByDataSource()
                                .asSequence()::first
                        )
                        .mapFailure { _ ->
                            DataSourceException(
                                DataSourceErrorResponse.DATASOURCE_SCHEMA_INTEGRITY_VIOLATION,
                                """composite_source_container_type must have at 
                                   |least one datasource defined: [ name: 
                                   |${context.compositeSourceContainerType.conventionalName} 
                                   |]
                                   |""".flattenIntoOneLine()
                            )
                        }
                        .map { entry ->
                            ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(
                                    SchemaDefinitionLanguageNamingConventions
                                        .OBJECT_TYPE_NAMING_CONVENTION
                                        .deriveName(entry.value)
                                        .toString()
                                )
                                .description(
                                    Description(
                                        "data_source: [ name: ${entry.key.name} ] [ container_type_name: ${entry.value.name} ]",
                                        SourceLocation.EMPTY,
                                        false
                                    )
                                )
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
