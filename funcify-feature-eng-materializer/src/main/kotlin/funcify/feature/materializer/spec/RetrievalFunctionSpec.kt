package funcify.feature.materializer.spec

import arrow.core.Either
import funcify.feature.schema.dataelementsource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.PersistentMap

interface RetrievalFunctionSpec {

    val dataSource: DataElementSource<*>

    val sourceVerticesByPath: PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>

    val parameterVerticesByPath: PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>

    fun updateSpec(transformer: SpecBuilder.() -> SpecBuilder): RetrievalFunctionSpec

    interface SpecBuilder {

        fun dataSource(dataSource: DataElementSource<*>): SpecBuilder

        fun addSourceVertex(sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>): SpecBuilder

        fun addSourceVertex(sourceJunctionVertex: SourceJunctionVertex): SpecBuilder

        fun addSourceVertex(sourceLeafVertex: SourceLeafVertex): SpecBuilder

        fun addParameterVertex(parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>): SpecBuilder

        fun addParameterVertex(parameterJunctionVertex: ParameterJunctionVertex): SpecBuilder

        fun addParameterVertex(parameterLeafVertex: ParameterLeafVertex): SpecBuilder

        fun build(): RetrievalFunctionSpec

    }
}
