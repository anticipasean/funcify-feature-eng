package funcify.feature.datasource.rest.swagger

import funcify.feature.datasource.rest.metadata.filter.SwaggerRestApiSourceMetadataFilter
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.SV3PWT
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.OpenAPI
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerV3ParserSourceIndexContext : SwaggerSourceIndexContextContainer<SV3PWT> {

    companion object {
        /** Witness type for [SwaggerV3ParserSourceIndexContext] */
        enum class SV3PWT

        fun SwaggerSourceIndexContextContainer<SV3PWT>.narrowed():
            SwaggerV3ParserSourceIndexContext {
            /*
             * Note: This is not an unsafe cast if the witness type (WT) parameter matches
             */
            return this as SwaggerV3ParserSourceIndexContext
        }
    }

    val swaggerAPIDataSourceKey: DataSource.Key<RestApiSourceIndex>

    val openAPI: OpenAPI

    val swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter

    val sourceContainerTypesBySchematicPath: ImmutableMap<SchematicPath, SwaggerSourceContainerType>

    val sourceAttributesBySchematicPath: ImmutableMap<SchematicPath, SwaggerSourceAttribute>

    val parameterContainerTypesBySchematicPath:
        ImmutableMap<SchematicPath, SwaggerParameterContainerType>

    val parameterAttributesBySchematicPath: ImmutableMap<SchematicPath, SwaggerParameterAttribute>

    fun update(transformer: Builder.() -> Builder): SwaggerV3ParserSourceIndexContext

    interface Builder {

        fun swaggerDataSourceKey(dataSourceKey: DataSource.Key<RestApiSourceIndex>): Builder

        fun openAPI(openAPI: OpenAPI): Builder

        fun swaggerRestApiSourceMetadataFilter(
            swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter
        ): Builder

        fun addSourceContainerTypeForPath(
            sourcePath: SchematicPath,
            sourceContainerType: SwaggerSourceContainerType
        ): Builder

        fun addSourceAttributeForPath(
            sourcePath: SchematicPath,
            sourceAttribute: SwaggerSourceAttribute
        ): Builder

        fun addParameterContainerTypeForPath(
            sourcePath: SchematicPath,
            parameterContainerType: SwaggerParameterContainerType
        ): Builder

        fun addParameterAttributeForPath(
            sourcePath: SchematicPath,
            parameterAttribute: SwaggerParameterAttribute
        ): Builder

        fun build(): SwaggerV3ParserSourceIndexContext
    }
}
