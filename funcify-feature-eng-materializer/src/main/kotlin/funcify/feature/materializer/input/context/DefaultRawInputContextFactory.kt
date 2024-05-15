package funcify.feature.materializer.input.context

import arrow.core.continuations.eagerEffect
import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.tools.json.JsonMapper
import funcify.feature.tools.json.MappingTarget.Companion.toKotlinObject
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap

/**
 * @author smccarron
 * @created 2023-07-30
 */
internal class DefaultRawInputContextFactory(private val jsonMapper: JsonMapper) : RawInputContextFactory {

    companion object {
        internal class DefaultBuilder(
            private val jsonMapper: JsonMapper,
            private var jsonRecord: JsonNode? = null,
            private var mapRecord: Map<*, *>? = null
        ) : RawInputContext.Builder {

            override fun json(jsonNode: JsonNode): RawInputContext.Builder {
                this.jsonRecord = jsonNode
                return this
            }

            override fun mapRecord(mapRecord: Map<*, *>): RawInputContext.Builder {
                this.mapRecord = mapRecord
                return this
            }

            override fun build(): RawInputContext {
                return eagerEffect<String, RawInputContext> {
                        ensure(jsonRecord != null || mapRecord != null) {
                            "both json and map_record input are null; one or the other must be provided"
                        }
                        when {
                            mapRecord != null -> {
                                val jsonByFieldName: PersistentMap<String, JsonNode> =
                                    try {
                                        jsonMapper
                                            .fromKotlinObject(mapRecord)
                                            .toKotlinObject<Map<String, JsonNode>>()
                                            .map(Map<String, JsonNode>::toPersistentMap)
                                            .orElseThrow()
                                    } catch (t: Throwable) {
                                        when (val message: String? = t.message) {
                                            null -> {
                                                shift(
                                                    "error without message [ type: %s ][ to_string: %s ]".format(
                                                        t.run { this::class }.qualifiedName,
                                                        t
                                                    )
                                                )
                                            }
                                            else -> {
                                                shift(message)
                                            }
                                        }
                                    }
                                DefaultJsonMapInputContext(jsonByFieldName = jsonByFieldName)
                            }
                            else -> {
                                DefaultJsonNodeInputContext(jsonNode = jsonRecord!!)
                            }
                        }
                    }
                    .fold({ message: String -> throw ServiceError.of(message) }, ::identity)
            }
        }
    }

    override fun builder(): RawInputContext.Builder {
        return DefaultBuilder(jsonMapper = jsonMapper)
    }
}
