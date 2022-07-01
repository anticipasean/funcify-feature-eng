package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.naming.DataSourceSDLDefinitionNamingConventions
import funcify.feature.datasource.sdl.SchematicGraphVertexTypeBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionNamingStrategy
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-06-30
 */
class FirstSourceAttributeFoundNamingStrategy :
    SchematicGraphVertexTypeBasedSDLDefinitionStrategy, SchematicVertexSDLDefinitionNamingStrategy {

    override val applicableSchematicGraphVertexTypes:
        ImmutableSet<SchematicGraphVertexType> by lazy {
        persistentSetOf(
            SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX,
            SchematicGraphVertexType.SOURCE_LEAF_VERTEX
        )
    }

    override fun determineNameForSDLDefinitionForSchematicVertexInContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<String> {
        return when (context) {
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                Try.attempt(
                        context
                            .compositeSourceAttribute
                            .getSourceAttributeByDataSource()
                            .asSequence()::first
                    )
                    .mapFailure { _ ->
                        DataSourceException(
                            DataSourceErrorResponse.DATASOURCE_SCHEMA_INTEGRITY_VIOLATION,
                            """composite_source_attribute must have at 
                                   |least one datasource defined: [ name: 
                                   |${context.compositeSourceAttribute.conventionalName} 
                                   |]
                                   |""".flattenIntoOneLine()
                        )
                    }
                    .map { (_, sourceAttr) ->
                        DataSourceSDLDefinitionNamingConventions.FIELD_NAMING_CONVENTION
                            .deriveName(sourceAttr)
                            .toString()
                    }
            }
            is SourceLeafVertexSDLDefinitionCreationContext -> {
                Try.attempt(
                        context
                            .compositeSourceAttribute
                            .getSourceAttributeByDataSource()
                            .asSequence()::first
                    )
                    .mapFailure { _ ->
                        DataSourceException(
                            DataSourceErrorResponse.DATASOURCE_SCHEMA_INTEGRITY_VIOLATION,
                            """composite_source_attribute must have at 
                                   |least one datasource defined: [ name: 
                                   |${context.compositeSourceAttribute.conventionalName} 
                                   |]
                                   |""".flattenIntoOneLine()
                        )
                    }
                    .map { (_, sourceAttr) ->
                        DataSourceSDLDefinitionNamingConventions.FIELD_NAMING_CONVENTION
                            .deriveName(sourceAttr)
                            .toString()
                    }
            }
            else -> {
                val clsNameInSnakeFormat =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                        FirstSourceAttributeFoundNamingStrategy::class.simpleName ?: ""
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
