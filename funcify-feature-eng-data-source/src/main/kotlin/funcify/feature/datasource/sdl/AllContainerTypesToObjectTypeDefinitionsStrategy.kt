package funcify.feature.datasource.sdl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.naming.SchemaDefinitionLanguageNamingConventions
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation

/**
 *
 * @author smccarron
 * @created 2022-06-27
 */
class AllContainerTypesToObjectTypeDefinitionsStrategy :
    SchematicVertexSDLDefinitionImplementationTypeSelectionStrategy {

    override fun determineSDLImplementationDefinitionForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                if (context.currentSDLDefinitionsForSchematicPath.isNotEmpty()) {
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
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                if (context.currentSDLDefinitionsForSchematicPath.isNotEmpty()) {
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
            is SourceLeafVertexSDLDefinitionCreationContext -> {
                TODO()
            }
            is ParameterJunctionVertexSDLDefinitionCreationContext -> {
                TODO()
            }
            is ParameterLeafVertexSDLDefinitionCreationContext -> {
                TODO()
            }
        }
    }
}
