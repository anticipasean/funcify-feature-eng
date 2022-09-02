package funcify.feature.datasource.retrieval

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.async.KFuture
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-09-01
 */
interface CacheableValue<V> {

    interface PlannedValue<V> {

        val contextualParameters: ImmutableMap<SchematicPath, JsonNode>

        val cacheRetrievalFunction: SingleSourceIndexJsonOptionCacheRetrievalFunction
    }

    interface PendingCacheValue<V> {

        val pendingCacheValue: KFuture<Option<JsonNode>>
    }

    interface CachedValue<V> {

        val cachedValue: JsonNode

        val valueAtTimestamp: Instant
    }

    interface PendingCalculationValue<V> {

        val pendingCalculationValue: KFuture<JsonNode>
    }

    interface CalculatedValue<V> {

        val calculatedValue: JsonNode

        val valueAtTimestamp: Instant
    }

    interface UntrackedValue<V> {}

    interface TrackedValue<V> {}
}
