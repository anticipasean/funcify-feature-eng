package funcify.feature.datasource.rest.metadata.reader

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.metadata.filter.SwaggerRestApiSourceMetadataFilter
import funcify.feature.datasource.rest.schema.DefaultSwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerPathGroupSourceContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerResponseTypeSourceContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerRestApiSourceMetamodel
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.swagger.DefaultSwaggerV3ParserSourceIndexContext
import funcify.feature.datasource.rest.swagger.DefaultSwaggerV3ParserSourceIndexFactory
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.narrowed
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexFactory
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import io.swagger.v3.oas.models.OpenAPI
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal class DefaultSwaggerRestApiSourceMetadataReader(
    private val swaggerRestApiSourceMetadataFilter: SwaggerRestApiSourceMetadataFilter
) : SwaggerRestApiSourceMetadataReader {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestApiSourceMetadataReader>()
    }

    override fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataSource.Key<RestApiSourceIndex>,
        metadataInput: OpenAPI,
    ): SourceMetamodel<RestApiSourceIndex> {
        val dataSourceNameAndType: String =
            dataSourceKey.name
                .toOption()
                .zip(dataSourceKey.dataSourceType.toString().toOption())
                .map { (n, t) -> "{ name: $n, type: $t }" }
                .orNull()
                ?: "<NA>"
        val openAPIPathsCountAndFirst: String =
            metadataInput
                .toOption()
                .mapNotNull { o -> o.paths }
                .flatMap { ps ->
                    ps.size
                        .toOption()
                        .zip(ps.asSequence().firstOrNull().toOption().mapNotNull { (p, _) -> p })
                        .map { (count, firstPath) ->
                            "{ paths.size: $count, first.path: $firstPath }"
                        }
                }
                .getOrElse { "{ paths.size: <NA>, first.path: <NA> }" }
        logger.debug(
            """read_source_metamodel_from_metadata: 
            |[ datasource.key: ${dataSourceNameAndType}, 
            |openapi: ${openAPIPathsCountAndFirst} 
            |]""".flatten()
        )
        val sourceIndicesCreationContext: SwaggerV3ParserSourceIndexContext =
            DefaultSwaggerV3ParserSourceIndexContext(
                swaggerAPIDataSourceKey = dataSourceKey,
                openAPI = metadataInput,
                swaggerRestApiSourceMetadataFilter = swaggerRestApiSourceMetadataFilter
            )
        val sourceIndexFactory: SwaggerV3ParserSourceIndexFactory =
            DefaultSwaggerV3ParserSourceIndexFactory()
        return Try.attempt {
                sourceIndexFactory.onOpenAPI(metadataInput, sourceIndicesCreationContext).narrowed()
            }
            .map { swaggerSourceIndexContext ->
                addChildAttributesToTheirParentIndices(swaggerSourceIndexContext)
            }
            .orElseThrow()
    }

    private fun addChildAttributesToTheirParentIndices(
        swaggerSourceIndexContext: SwaggerV3ParserSourceIndexContext
    ): SourceMetamodel<RestApiSourceIndex> {
        val updatedSourceContainerTypesByPath =
            swaggerSourceIndexContext.sourceAttributesBySchematicPath
                .asSequence()
                .map { (sourcePath, sourceAttr) ->
                    sourcePath.getParentPath().map { pp -> pp to sourceAttr }
                }
                .flatMapOptions()
                .groupBy({ (parentPath, _) -> parentPath }, { (_, sourceAttr) -> sourceAttr })
                .asSequence()
                .sortedBy { (path, _) -> path }
                .fold(
                    swaggerSourceIndexContext.sourceContainerTypesBySchematicPath.toPersistentMap()
                ) { sctByPath, (parentPath, sourceAttrs) ->
                    sctByPath[parentPath]
                        .toOption()
                        .fold(
                            { sctByPath },
                            { sct ->
                                when (sct) {
                                    is DefaultSwaggerResponseTypeSourceContainerType -> {
                                        sctByPath.put(
                                            parentPath,
                                            sct.copy(
                                                sourceAttributes = sourceAttrs.toPersistentSet()
                                            )
                                        )
                                    }
                                    is DefaultSwaggerPathGroupSourceContainerType -> {
                                        sctByPath.put(
                                            parentPath,
                                            sct.copy(
                                                sourceAttributes = sourceAttrs.toPersistentSet()
                                            )
                                        )
                                    }
                                    else -> {
                                        throw RestApiDataSourceException(
                                            RestApiErrorResponse.UNEXPECTED_ERROR,
                                            """unhandled swagger_source_container_type [ actual: ${sct::class.qualifiedName} ]"""
                                        )
                                    }
                                }
                            }
                        )
                }
        val updatedParameterContainerTypesByPath =
            swaggerSourceIndexContext.parameterAttributesBySchematicPath
                .asSequence()
                .map { (sourcePath, paramAttr) ->
                    sourcePath.getParentPath().map { pp -> pp to paramAttr }
                }
                .flatMapOptions()
                .groupBy({ (parentPath, _) -> parentPath }, { (_, paramAttr) -> paramAttr })
                .asSequence()
                .sortedBy { (path, _) -> path }
                .fold(
                    swaggerSourceIndexContext.parameterContainerTypesBySchematicPath
                        .toPersistentMap()
                ) { pctByPath, (parentPath, paramAttrs) ->
                    pctByPath[parentPath]
                        .toOption()
                        .fold(
                            { pctByPath },
                            { pct ->
                                when (pct) {
                                    is DefaultSwaggerParameterContainerType -> {
                                        pctByPath.put(
                                            parentPath,
                                            pct.copy(
                                                parameterAttributes = paramAttrs.toPersistentSet()
                                            )
                                        )
                                    }
                                    else -> {
                                        throw RestApiDataSourceException(
                                            RestApiErrorResponse.UNEXPECTED_ERROR,
                                            """unhandled swagger_parameter_container_type [ actual: ${pct::class.qualifiedName} ]"""
                                        )
                                    }
                                }
                            }
                        )
                }

        return DefaultSwaggerRestApiSourceMetamodel(
            sourceIndicesByPath =
                sequenceOf(
                        updatedSourceContainerTypesByPath,
                        swaggerSourceIndexContext.sourceAttributesBySchematicPath,
                        updatedParameterContainerTypesByPath,
                        swaggerSourceIndexContext.parameterAttributesBySchematicPath
                    )
                    .flatMap { m -> m.asSequence() }
                    .sortedBy { (path, _) -> path }
                    .reduceEntriesToPersistentSetValueMap()
        )
    }
}
