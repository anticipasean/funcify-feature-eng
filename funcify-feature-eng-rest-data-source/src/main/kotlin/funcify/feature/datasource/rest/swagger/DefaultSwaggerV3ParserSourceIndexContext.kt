package funcify.feature.datasource.rest.swagger

import arrow.core.toOption
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Builder
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.springframework.http.HttpMethod

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerV3ParserSourceIndexContext(
    override val openAPI: OpenAPI,
    override val sourcePathParentByChildPath: PersistentMap<SchematicPath, SchematicPath> =
        persistentMapOf(),
    override val pathItemsBySourcePath: PersistentMap<SchematicPath, PathItem> = persistentMapOf(),
    override val httpMethodToRequestBodyPairsForSchematicPath:
        PersistentMap<SchematicPath, PersistentSet<Pair<HttpMethod, RequestBody>>> =
        persistentMapOf(),
    override val httpMethodToResponseBodiesBySchematicPath:
        PersistentMap<SchematicPath, PersistentSet<Pair<HttpMethod, ApiResponses>>> =
        persistentMapOf(),
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
        persistentMapOf()
) : SwaggerV3ParserSourceIndexContext {

    companion object {

        internal data class DefaultBuilder(
            private var openAPI: OpenAPI,
            private var sourcePathParentByChildPath:
                PersistentMap.Builder<SchematicPath, SchematicPath>,
            private var pathItemsBySourcePath: PersistentMap.Builder<SchematicPath, PathItem>,
            private var httpMethodToRequestBodyPairsForSchematicPath:
                PersistentMap.Builder<SchematicPath, PersistentSet<Pair<HttpMethod, RequestBody>>>,
            private var httpMethodToResponseBodiesBySchematicPath:
                PersistentMap.Builder<SchematicPath, PersistentSet<Pair<HttpMethod, ApiResponses>>>,
            private var sourceContainerTypesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerSourceContainerType>,
            private var sourceAttributesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerSourceAttribute>,
            private var parameterContainerTypesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerParameterContainerType>,
            private var parameterAttributesBySchematicPath:
                PersistentMap.Builder<SchematicPath, SwaggerParameterAttribute>
        ) : Builder {

            override fun openAPI(openAPI: OpenAPI): Builder {
                this.openAPI = openAPI
                return this
            }

            override fun addChildPathForParentPath(
                childPath: SchematicPath,
                parentPath: SchematicPath
            ): Builder {
                this.sourcePathParentByChildPath[childPath] = parentPath
                return this
            }

            override fun addPathItemForPath(
                sourcePath: SchematicPath,
                pathItem: PathItem
            ): Builder {
                this.pathItemsBySourcePath[sourcePath] = pathItem
                return this
            }

            override fun addHttpMethodRequestBodyPairsForSchematicPathForPathItem(
                sourcePath: SchematicPath,
                pathItem: PathItem
            ): Builder {
                val httpMethodRequestBodyPairsSet: PersistentSet<Pair<HttpMethod, RequestBody>> =
                    HttpMethod.values().fold(persistentSetOf<Pair<HttpMethod, RequestBody>>()) {
                        reqBodyPairsSet,
                        httpMethod ->
                        when (httpMethod) {
                            HttpMethod.GET ->
                                pathItem.get.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.HEAD ->
                                pathItem.head.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.POST ->
                                pathItem.post.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.PUT ->
                                pathItem.put.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.PATCH ->
                                pathItem.patch.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.DELETE ->
                                pathItem.delete.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.OPTIONS ->
                                pathItem.options.toOption().mapNotNull { op -> op.requestBody }
                            HttpMethod.TRACE ->
                                pathItem.trace.toOption().mapNotNull { op -> op.requestBody }
                        }.fold(
                            { reqBodyPairsSet },
                            { reqBody -> reqBodyPairsSet.add(httpMethod to reqBody) }
                        )
                    }
                this.httpMethodToRequestBodyPairsForSchematicPath[sourcePath] =
                    httpMethodRequestBodyPairsSet
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
                    openAPI = openAPI,
                    sourcePathParentByChildPath = sourcePathParentByChildPath.build(),
                    pathItemsBySourcePath = pathItemsBySourcePath.build(),
                    httpMethodToRequestBodyPairsForSchematicPath =
                        httpMethodToRequestBodyPairsForSchematicPath.build(),
                    httpMethodToResponseBodiesBySchematicPath =
                        httpMethodToResponseBodiesBySchematicPath.build(),
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
                openAPI = openAPI,
                sourcePathParentByChildPath = sourcePathParentByChildPath.builder(),
                pathItemsBySourcePath = pathItemsBySourcePath.builder(),
                httpMethodToRequestBodyPairsForSchematicPath =
                    httpMethodToRequestBodyPairsForSchematicPath.builder(),
                httpMethodToResponseBodiesBySchematicPath =
                    httpMethodToResponseBodiesBySchematicPath.builder(),
                sourceContainerTypesBySchematicPath = sourceContainerTypesBySchematicPath.builder(),
                sourceAttributesBySchematicPath = sourceAttributesBySchematicPath.builder(),
                parameterContainerTypesBySchematicPath =
                    parameterContainerTypesBySchematicPath.builder(),
                parameterAttributesBySchematicPath = parameterAttributesBySchematicPath.builder()
            )
        return transformer.invoke(builder).build()
    }
}
