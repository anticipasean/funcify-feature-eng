package funcify.feature.materializer.input

import arrow.core.continuations.eagerEffect
import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import kotlinx.collections.immutable.toPersistentMap

/**
 * @author smccarron
 * @created 2023-07-30
 */
internal class DefaultRawInputContextFactory : RawInputContextFactory {

    companion object {
        internal class DefaultBuilder(
            private var json: JsonNode? = null,
            private var csvRecord: Map<String, String?>? = null,
        ) : RawInputContext.Builder {

            override fun json(jsonNode: JsonNode): RawInputContext.Builder {
                this.json = jsonNode
                return this
            }

            override fun csvRecord(csvRecord: Map<String, String?>): RawInputContext.Builder {
                this.csvRecord = csvRecord
                return this
            }

            override fun build(): RawInputContext {
                return eagerEffect<String, RawInputContext> {
                        ensure(json != null || csvRecord != null) {
                            "both json and csv_record input are null; one or the other must be provided"
                        }
                        when {
                            csvRecord != null -> {
                                DefaultCommaSeparatedValuesInputContext(
                                    csvRecord!!.toPersistentMap()
                                )
                            }
                            else -> {
                                when {
                                    json!!.isObject &&
                                        json!!.size() >= 1 &&
                                        json!!.fields().asSequence().all { (_: String, v: JsonNode)
                                            ->
                                            !v.isContainerNode
                                        } -> {
                                        DefaultTabularJsonInputContext(json!!)
                                    }
                                    else -> {
                                        DefaultStandardJsonInputContext(json!!)
                                    }
                                }
                            }
                        }
                    }
                    .fold({ message: String -> throw ServiceError.of(message) }, ::identity)
            }
        }
    }

    override fun builder(): RawInputContext.Builder {
        return DefaultBuilder()
    }
}
