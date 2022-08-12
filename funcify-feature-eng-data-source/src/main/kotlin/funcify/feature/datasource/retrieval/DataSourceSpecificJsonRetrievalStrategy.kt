package funcify.feature.datasource.retrieval

import arrow.core.Either
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-08-12
 */
interface DataSourceSpecificJsonRetrievalStrategy<SI : SourceIndex<SI>> :
    SchematicPathBasedJsonRetrievalFunction {

    override val dataSource: DataSource<SI>

    override val parameterPaths: ImmutableSet<SchematicPath>
        get() =
            parameterVertices
                .asSequence()
                .map { pjvOrPlv ->
                    pjvOrPlv.fold(ParameterJunctionVertex::path, ParameterLeafVertex::path)
                }
                .toImmutableSet()
    override val sourcePaths: ImmutableSet<SchematicPath>
        get() =
            sourceVertices
                .asSequence()
                .map { sjvOrSlv ->
                    sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                }
                .toImmutableSet()

    val parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>

    val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
}
