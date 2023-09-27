package funcify.feature.schema.json

import arrow.core.None
import arrow.core.Option
import arrow.core.toOption
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectedField
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.slf4j.Logger

object JsonNodeValueExtractionByOperationPath : (JsonNode, GQLOperationPath) -> Option<JsonNode> {

    private val logger: Logger = loggerFor<JsonNodeValueExtractionByOperationPath>()

    private val jsonPointerForPath: (GQLOperationPath) -> Option<JsonPointer> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, JsonPointer> = ConcurrentHashMap()
        val pointerCalculation: (GQLOperationPath) -> JsonPointer? = { p: GQLOperationPath ->
            try {
                p.selection
                    .asSequence()
                    .map { ss: SelectionSegment ->
                        when (ss) {
                            is FieldSegment -> {
                                ss.fieldName
                            }
                            is AliasedFieldSegment -> {
                                ss.alias
                            }
                            is InlineFragmentSegment -> {
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        sf.alias
                                    }
                                    is FieldSegment -> {
                                        sf.fieldName
                                    }
                                }
                            }
                            is FragmentSpreadSegment -> {
                                when (val sf: SelectedField = ss.selectedField) {
                                    is AliasedFieldSegment -> {
                                        sf.alias
                                    }
                                    is FieldSegment -> {
                                        sf.fieldName
                                    }
                                }
                            }
                        }
                    }
                    .joinToString("/", "/")
                    .let { expression: String -> JsonPointer.compile(expression) }
            } catch (e: Exception) {
                // TODO: Remove logging after setup has been tested
                logger.warn(
                    """error occurred when creating json_pointer for: 
                    |[ path: {} ]
                    |[ type: {}, message: {} ]"""
                        .flatten(),
                    p,
                    e::class.simpleName,
                    e.message
                )
                null
            }
        }
        { path: GQLOperationPath -> cache.computeIfAbsent(path, pointerCalculation).toOption() }
    }

    override fun invoke(
        topLevelJsonNode: JsonNode,
        pathToExtract: GQLOperationPath
    ): Option<JsonNode> {
        // TODO: Contract and logic needs to be adjusted for handling of JSON ArrayNode sub nodes
        return jsonPointerForPath.invoke(pathToExtract).flatMap { jp: JsonPointer ->
            Option.catch(
                { t: Throwable ->
                    logger.warn(
                        """error occurred when attempting to extract JSON at 
                        |[ pointer: {} ] for [ path: {} ]: 
                        |[ type: {}, message: {} ]"""
                            .flatten(),
                        jp,
                        pathToExtract,
                        t::class.simpleName,
                        t.message
                    )
                    None
                },
                { topLevelJsonNode.at(jp) }
            )
        }
    }
}
