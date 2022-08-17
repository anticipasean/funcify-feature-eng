package funcify.feature.materializer.schema

import arrow.core.Either
import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunction
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge.SpecBuilder
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface RequestParameterEdge : SchematicEdge {

    fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge

    interface MaterializedValueRequestParameterEdge : RequestParameterEdge {
        val materializedJsonValue: JsonNode
    }

    interface MissingContextValueRequestParameterEdge: RequestParameterEdge {

    }

    interface RetrievalFunctionSpecRequestParameterEdge : RequestParameterEdge {
        val dataSource: DataSource<*>
        val sourceVerticesByPath:
            PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>
        val parameterVerticesByPath:
            PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>

        fun updateSpec(
            transformer: SpecBuilder.() -> SpecBuilder
        ): RetrievalFunctionSpecRequestParameterEdge

        interface SpecBuilder {
            fun dataSource(dataSource: DataSource<*>): SpecBuilder
            fun addSourceVertex(sourceJunctionVertex: SourceJunctionVertex): SpecBuilder
            fun addSourceVertex(sourceLeafVertex: SourceLeafVertex): SpecBuilder
            fun addParameterVertex(parameterJunctionVertex: ParameterJunctionVertex): SpecBuilder
            fun addParameterVertex(parameterLeafVertex: ParameterLeafVertex): SpecBuilder
            fun build(): RetrievalFunctionSpecRequestParameterEdge
        }
    }

    interface DependentValueRequestParameterEdge : RequestParameterEdge {
        val extractionFunction: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
    }

    interface RetrievalFunctionValueRequestParameterEdge : RequestParameterEdge {
        val retrievalFunction: SchematicPathBasedJsonRetrievalFunction
    }

    interface Builder {

        fun fromPathToPath(path1: SchematicPath, path2: SchematicPath): Builder

        fun materializedValue(materializedJsonNode: JsonNode): Builder

        fun missingContextValue(): Builder

        fun retrievalFunctionSpecForDataSource(
            dataSource: DataSource<*>,
            specCreator: SpecBuilder.() -> SpecBuilder
        ): Builder

        fun retrievalFunction(retrievalFunction: SchematicPathBasedJsonRetrievalFunction): Builder

        fun extractionFromAncestorFunction(
            extractor: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
        ): Builder

        fun build(): RequestParameterEdge
    }
}
