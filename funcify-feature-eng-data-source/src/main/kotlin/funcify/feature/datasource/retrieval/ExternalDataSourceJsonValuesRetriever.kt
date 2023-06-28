package funcify.feature.datasource.retrieval

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface ExternalDataSourceJsonValuesRetriever :
    (ImmutableMap<SchematicPath, JsonNode>) -> Mono<ImmutableMap<SchematicPath, JsonNode>> {

    val dataSourceKey: DataElementSource.Key<*>
        get() = dataSource.key

    val dataSource: DataElementSource<*>

    val parameterPaths: ImmutableSet<SchematicPath>

    val sourcePaths: ImmutableSet<SchematicPath>

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Mono<ImmutableMap<SchematicPath, JsonNode>>

    interface Builder {

        fun dataSource(dataSource: DataElementSource<*>): Builder

        fun addRequestParameter(
            parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>
        ): Builder

        fun addRequestParameter(parameterJunctionVertex: ParameterJunctionVertex): Builder

        fun addRequestParameter(parameterLeafVertex: ParameterLeafVertex): Builder

        fun addSourceTarget(
            sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>
        ): Builder

        fun addSourceTarget(sourceJunctionVertex: SourceJunctionVertex): Builder

        fun addSourceTarget(sourceLeafVertex: SourceLeafVertex): Builder

        fun build(): Try<ExternalDataSourceJsonValuesRetriever>
    }
}
