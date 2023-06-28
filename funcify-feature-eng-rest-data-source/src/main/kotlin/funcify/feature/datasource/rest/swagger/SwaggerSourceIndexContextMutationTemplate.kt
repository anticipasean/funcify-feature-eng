package funcify.feature.datasource.rest.swagger

import arrow.core.Option
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-11
 */
interface SwaggerSourceIndexContextMutationTemplate<WT, O, P, REQ, RES, SCH> {

    fun getDataSourceKeyForSwaggerSourceIndicesInContext(
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): DataElementSource.Key<RestApiSourceIndex>

    fun shouldIncludeSourcePathAndPathInfo(
        sourcePath: SchematicPath,
        servicePathName: String,
        pathInfo: P,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Boolean

    fun getOpenAPIRepresentationInContext(
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): O

    fun addNewOrUpdateExistingSwaggerSourceIndexToContext(
        newSwaggerSourceIndex: SwaggerRestApiSourceIndex,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun getExistingSwaggerSourceContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerSourceContainerType>

    fun getExistingSwaggerSourceAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerSourceAttribute>

    fun getExistingSwaggerParameterContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerParameterContainerType>

    fun getExistingSwaggerParameterAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerParameterAttribute>
}
