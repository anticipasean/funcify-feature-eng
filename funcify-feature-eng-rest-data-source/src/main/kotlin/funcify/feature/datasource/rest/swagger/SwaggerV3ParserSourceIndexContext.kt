package funcify.feature.datasource.rest.swagger

import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.SV3PWT
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.springframework.http.HttpMethod

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerV3ParserSourceIndexContext :
    SwaggerSourceIndexContextContainer<SV3PWT, OpenAPI, PathItem, RequestBody, ApiResponse> {

    companion object {
        /** Witness type for [SwaggerV3ParserSourceIndexContext] */
        enum class SV3PWT

        fun <O, P, REQ, RES> SwaggerSourceIndexContextContainer<SV3PWT, O, P, REQ, RES>.narrowed():
            SwaggerV3ParserSourceIndexContext {
            /*
             * Note: This is not an unsafe cast if the witness type (WT) parameter matches
             */
            return this as SwaggerV3ParserSourceIndexContext
        }
    }

    val openAPI: OpenAPI

    val sourcePathParentByChildPath: ImmutableMap<SchematicPath, SchematicPath>

    val pathItemsBySourcePath: ImmutableMap<SchematicPath, PathItem>

    /**
     * only interested in requests with a JSON "body" so definitely POSTs and potentially PUTs and
     * DELETEs
     */
    val httpMethodToRequestBodyPairsForSchematicPath:
        ImmutableMap<SchematicPath, ImmutableSet<Pair<HttpMethod, RequestBody>>>
    /** only interested in responses with a JSON "body" */
    val httpMethodToResponseBodiesBySchematicPath:
        ImmutableMap<SchematicPath, ImmutableSet<Pair<HttpMethod, ApiResponses>>>

    val sourceContainerTypesBySchematicPath: ImmutableMap<SchematicPath, SwaggerSourceContainerType>

    val sourceAttributesBySchematicPath: ImmutableMap<SchematicPath, SwaggerSourceAttribute>

    val parameterContainerTypesBySchematicPath:
        ImmutableMap<SchematicPath, SwaggerParameterContainerType>

    val parameterAttributesBySchematicPath: ImmutableMap<SchematicPath, SwaggerParameterAttribute>

    fun update(transformer: Builder.() -> Builder): SwaggerV3ParserSourceIndexContext

    interface Builder {

        fun openAPI(openAPI: OpenAPI): Builder

        fun addChildPathForParentPath(childPath: SchematicPath, parentPath: SchematicPath): Builder

        fun addPathItemForPath(sourcePath: SchematicPath, pathItem: PathItem): Builder

        fun addHttpMethodRequestBodyPairsForSchematicPathForPathItem(
            sourcePath: SchematicPath,
            pathItem: PathItem
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
