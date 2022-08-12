package funcify.feature.materializer.tools

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunction :
    (ImmutableMap<SchematicPath, JsonNode>) -> Deferred<JsonNode> {

    val dataSourceKey: DataSource.Key<*>
        get() = dataSource.key

    val dataSource: DataSource<*>

    val parameterPaths: ImmutableSet<SchematicPath>

    val sourcePaths: ImmutableSet<SchematicPath>

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<JsonNode>

    fun update(transformer: Builder.() -> Builder): Try<SchematicPathBasedJsonRetrievalFunction>

    interface Builder {

        fun dataSource(dataSource: DataSource<*>): Builder

        fun addRequestParameter(parameterJunctionVertex: ParameterJunctionVertex): Builder

        fun addRequestParameter(parameterLeafVertex: ParameterLeafVertex): Builder

        fun addSourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder

        fun addSourceTarget(sourceLeafVertex: SourceLeafVertex): Builder

        fun build(): Try<SchematicPathBasedJsonRetrievalFunction>
    }
}
