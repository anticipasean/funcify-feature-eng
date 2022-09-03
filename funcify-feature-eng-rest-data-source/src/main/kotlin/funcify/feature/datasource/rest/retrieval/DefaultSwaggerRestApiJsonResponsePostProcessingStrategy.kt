package funcify.feature.datasource.rest.retrieval

import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.json.JsonNodeSchematicPathToValueMappingExtractor
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-29
 */
internal class DefaultSwaggerRestApiJsonResponsePostProcessingStrategy :
    SwaggerRestApiJsonResponsePostProcessingStrategy {

    companion object {
        private val logger: Logger = loggerFor<SwaggerRestApiJsonResponsePostProcessingStrategy>()
    }

    override fun postProcessRestApiJsonResponse(
        context: SwaggerRestApiJsonResponsePostProcessingContext,
        responseJsonNode: JsonNode,
    ): Mono<ImmutableMap<SchematicPath, JsonNode>> {
        logger.debug(
            "post_process_rest_api_json_response: [ response_json_node.type: ${responseJsonNode.nodeType} ]"
        )
        return Mono.just(
            JsonNodeSchematicPathToValueMappingExtractor.invoke(responseJsonNode)
                .asSequence()
                .map { (sourcePath, jsonValue) ->
                    SchematicPath.of {
                        pathSegments(
                            context.parentVertexPathToSwaggerSourceAttribute.second.sourcePath
                                .pathSegments
                                .asSequence()
                                .plus(sourcePath.pathSegments)
                                .toList()
                        )
                    } to jsonValue
                }
                .map { (remappedSourcePath, jsonValue) ->
                    context.sourceVertexPathBySourceIndexPath[remappedSourcePath].toOption().map {
                        sourceVertexPath ->
                        sourceVertexPath to jsonValue
                    }
                }
                .flatMapOptions()
                .reducePairsToPersistentMap()
        )
    }
}
