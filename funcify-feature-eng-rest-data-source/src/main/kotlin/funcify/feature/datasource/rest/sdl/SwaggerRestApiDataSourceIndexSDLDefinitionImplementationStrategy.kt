package funcify.feature.datasource.rest.sdl

import arrow.core.filterIsInstance
import arrow.core.toOption
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceMetamodel
import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy
import funcify.feature.datasource.sdl.DataSourceBasedSDLDefinitionStrategy.DataSourceAttribute
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-17
 */
internal class SwaggerRestApiDataSourceIndexSDLDefinitionImplementationStrategy(
    private val restApiDataSource: RestApiDataSource
) :
    DataSourceBasedSDLDefinitionStrategy<SchematicVertexSDLDefinitionCreationContext<*>>,
    SchematicVertexSDLDefinitionImplementationStrategy {

    init {
        restApiDataSource
            .toOption()
            .filterIsInstance<SwaggerRestApiSourceMetamodel>()
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.INVALID_INPUT,
                    """rest_api_data_source must have a 
                        |swagger_rest_api_source_metamodel defined 
                        |for use of this 
                        |sdl_definition_implementation_strategy: [ 
                        |actual: data_source.source_metamodel.type: 
                        |${restApiDataSource.sourceMetamodel::class.qualifiedName}, 
                        |expected: ${SwaggerRestApiSourceMetamodel::class.qualifiedName} 
                        |]""".flattenIntoOneLine()
                )
            }
            .orElseThrow()
    }

    override val expectedDataSourceAttributeValues: ImmutableSet<DataSourceAttribute<*>> by lazy {
        persistentSetOf(
            DataSourceBasedSDLDefinitionStrategy.dataSourceNameAttribute(
                expectedName = restApiDataSource.name
            ),
            DataSourceBasedSDLDefinitionStrategy.dataSourceTypeAttribute(
                expectedDataSourceType = RawDataSourceType.REST_API
            ),
            DataSourceBasedSDLDefinitionStrategy.sourceIndexTypeAttribute(
                expectedSourceIndexType = RestApiSourceIndex::class
            )
        )
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        TODO()
    }
}
