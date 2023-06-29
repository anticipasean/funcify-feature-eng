package funcify.feature.datasource.rest.swagger

import funcify.feature.datasource.rest.metadata.filter.SwaggerRestApiSourceMetadataFilter
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Builder
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.OpenAPI
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerV3ParserSourceIndexContext(
    override val swaggerAPIDataSourceKey: DataElementSource.Key<RestApiSourceIndex>,
    override val openAPI: OpenAPI,
    override val swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter =
        SwaggerRestApiSourceMetadataFilter.INCLUDE_ALL_FILTER,
    override val sourceContainerTypesBySchematicPath:
        PersistentMap<SchematicPath, SwaggerSourceContainerType> =
        persistentMapOf(),
    override val sourceAttributesBySchematicPath:
        PersistentMap<SchematicPath, SwaggerSourceAttribute> =
        persistentMapOf(),
    override val parameterContainerTypesBySchematicPath:
        PersistentMap<SchematicPath, SwaggerParameterContainerType> =
        persistentMapOf(),
    override val parameterAttributesBySchematicPath:
        PersistentMap<SchematicPath, SwaggerParameterAttribute> =
        persistentMapOf(),
) : SwaggerV3ParserSourceIndexContext {

    companion object {

        internal data class DefaultBuilder(
            private var swaggerAPIDataSourceKey: DataElementSource.Key<RestApiSourceIndex>,
            private var openAPI: OpenAPI,
            private var swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter,
            private var sourceContainerTypesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerSourceContainerType>,
            private var sourceAttributesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerSourceAttribute>,
            private var parameterContainerTypesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerParameterContainerType>,
            private var parameterAttributesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerParameterAttribute>
        ) : Builder {

            override fun swaggerDataSourceKey(
                dataSourceKey: DataElementSource.Key<RestApiSourceIndex>
            ): Builder {
                this.swaggerAPIDataSourceKey = dataSourceKey
                return this
            }

            override fun openAPI(openAPI: OpenAPI): Builder {
                this.openAPI = openAPI
                return this
            }

            override fun swaggerRestApiSourceMetadataFilter(
                swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter
            ): Builder {
                this.swaggerRestApiSourceMetadataFilter = swaggerRestApiSourceMetadataFilter
                return this
            }

            override fun addSourceContainerTypeForPath(
                sourcePath: SchematicPath,
                sourceContainerType: SwaggerSourceContainerType
            ): Builder {
                this.sourceContainerTypesBySchematicPath[sourcePath] = sourceContainerType
                return this
            }

            override fun addSourceAttributeForPath(
                sourcePath: SchematicPath,
                sourceAttribute: SwaggerSourceAttribute
            ): Builder {
                this.sourceAttributesBySchematicPath[sourcePath] = sourceAttribute
                return this
            }

            override fun addParameterContainerTypeForPath(
                sourcePath: SchematicPath,
                parameterContainerType: SwaggerParameterContainerType,
            ): Builder {
                this.parameterContainerTypesBySchematicPath[sourcePath] = parameterContainerType
                return this
            }

            override fun addParameterAttributeForPath(
                sourcePath: SchematicPath,
                parameterAttribute: SwaggerParameterAttribute
            ): Builder {
                this.parameterAttributesBySchematicPath[sourcePath] = parameterAttribute
                return this
            }

            override fun build(): SwaggerV3ParserSourceIndexContext {
                return DefaultSwaggerV3ParserSourceIndexContext(
                    swaggerAPIDataSourceKey = swaggerAPIDataSourceKey,
                    openAPI = openAPI,
                    swaggerRestApiSourceMetadataFilter = swaggerRestApiSourceMetadataFilter,
                    sourceContainerTypesBySchematicPath =
                        sourceContainerTypesBySchematicPath.build(),
                    sourceAttributesBySchematicPath = sourceAttributesBySchematicPath.build(),
                    parameterContainerTypesBySchematicPath =
                        parameterContainerTypesBySchematicPath.build(),
                    parameterAttributesBySchematicPath = parameterAttributesBySchematicPath.build(),
                )
            }
        }
    }

    override fun update(transformer: Builder.() -> Builder): SwaggerV3ParserSourceIndexContext {
        val builder =
            DefaultBuilder(
                swaggerAPIDataSourceKey = swaggerAPIDataSourceKey,
                openAPI = openAPI,
                swaggerRestApiSourceMetadataFilter = swaggerRestApiSourceMetadataFilter,
                sourceContainerTypesBySchematicPath = sourceContainerTypesBySchematicPath.builder(),
                sourceAttributesBySchematicPath = sourceAttributesBySchematicPath.builder(),
                parameterContainerTypesBySchematicPath =
                    parameterContainerTypesBySchematicPath.builder(),
                parameterAttributesBySchematicPath = parameterAttributesBySchematicPath.builder()
            )
        return transformer.invoke(builder).build()
    }
}
