package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.naming.DataSourceSDLDefinitionNamingConventions
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-06-30
 */
class FirstSourceContainerTypeFoundNamingStrategy :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy<String>,
    SchematicVertexSDLDefinitionNamingStrategy {

    override val applicableSchematicGraphVertexTypes:
        ImmutableSet<SchematicGraphVertexType> by lazy {
        persistentSetOf(
            SchematicGraphVertexType.SOURCE_ROOT_VERTEX,
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX
        )
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<String> {
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
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
                               |""".flatten()
                        )
                    }
                    .map { (_, sourceContTyp) ->
                        DataSourceSDLDefinitionNamingConventions.OBJECT_TYPE_NAMING_CONVENTION
                            .deriveName(sourceContTyp)
                            .toString()
                    }
            }
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
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
                               |""".flatten()
                        )
                    }
                    .map { (_, sourceContTyp) ->
                        DataSourceSDLDefinitionNamingConventions.OBJECT_TYPE_NAMING_CONVENTION
                            .deriveName(sourceContTyp)
                            .toString()
                    }
            }
            else -> {
                val clsNameInSnakeFormat =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                        FirstSourceContainerTypeFoundNamingStrategy::class.simpleName ?: ""
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
