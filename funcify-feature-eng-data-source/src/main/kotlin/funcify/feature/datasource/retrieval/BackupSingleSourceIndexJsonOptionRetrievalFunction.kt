package funcify.feature.datasource.retrieval

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-28
 */
fun interface BackupSingleSourceIndexJsonOptionRetrievalFunction :
    (ImmutableMap<SchematicPath, Deferred<Option<JsonNode>>>) -> Deferred<JsonNode> {

    override fun invoke(
        parameterValuesByPath: ImmutableMap<SchematicPath, Deferred<Option<JsonNode>>>
    ): Deferred<JsonNode>
}
