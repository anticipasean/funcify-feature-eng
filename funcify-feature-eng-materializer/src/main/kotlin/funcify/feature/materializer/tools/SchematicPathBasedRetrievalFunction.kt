package funcify.feature.materializer.tools

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.async.KFuture
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedRetrievalFunction: (ImmutableMap<SchematicPath, JsonNode>) -> KFuture<JsonNode> {

    val namedParameterPaths: ImmutableSet<SchematicPath>

}
