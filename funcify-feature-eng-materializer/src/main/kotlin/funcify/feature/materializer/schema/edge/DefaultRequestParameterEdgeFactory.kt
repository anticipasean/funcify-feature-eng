package funcify.feature.materializer.schema.edge

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.schema.edge.RequestParameterEdge.Builder
import funcify.feature.materializer.schema.edge.RequestParameterEdge.DependentValueRequestParameterEdge
import funcify.feature.materializer.schema.edge.RequestParameterEdge.MaterializedValueRequestParameterEdge
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultRequestParameterEdgeFactory : RequestParameterEdgeFactory {

    companion object {

        internal data class DefaultMaterializedValueRequestParameterEdge(
            override val id: Pair<GQLOperationPath, GQLOperationPath>,
            override val materializedJsonValue: JsonNode
        ) : MaterializedValueRequestParameterEdge {}

        internal data class DefaultDependentValueRequestParameterEdge(
            override val id: Pair<GQLOperationPath, GQLOperationPath>,
            override val extractionFunction:
                (ImmutableMap<GQLOperationPath, JsonNode>) -> Option<JsonNode>
        ) : DependentValueRequestParameterEdge {}

        internal data class DefaultBuilder(
            private var pathPair: Pair<GQLOperationPath, GQLOperationPath>? = null,
            private var materializedJsonNode: JsonNode? = null,
            private var extractor: ((ImmutableMap<GQLOperationPath, JsonNode>) -> Option<JsonNode>)? =
                null
        ) : Builder {

            override fun fromPathToPath(path1: GQLOperationPath, path2: GQLOperationPath): Builder {
                this.pathPair = path1 to path2
                return this
            }

            override fun fromPathToPath(edgeKey: Pair<GQLOperationPath, GQLOperationPath>): Builder {
                this.pathPair = edgeKey
                return this
            }

            override fun materializedValue(materializedJsonNode: JsonNode): Builder {
                this.materializedJsonNode = materializedJsonNode
                return this
            }

            override fun dependentExtractionFunction(
                extractor: (ImmutableMap<GQLOperationPath, JsonNode>) -> Option<JsonNode>
            ): Builder {
                this.extractor = extractor
                return this
            }

            override fun build(): RequestParameterEdge {
                return when {
                    pathPair == null -> {
                        throw IllegalArgumentException(
                            "path_pair: [ from path to path ] must be provided for edge creation"
                        )
                    }
                    materializedJsonNode != null -> {
                        DefaultMaterializedValueRequestParameterEdge(
                            pathPair!!,
                            materializedJsonNode!!
                        )
                    }
                    extractor != null -> {
                        DefaultDependentValueRequestParameterEdge(pathPair!!, extractor!!)
                    }
                    else -> {
                        // TODO: Complete list of edge parameter types when ready and convert to
                        // module exception typ
                        throw IllegalArgumentException(
                            "one or more of the following arguments is missing for creation of a request_parameter_edge: ["
                        )
                    }
                }
            }
        }
    }

    override fun builder(): Builder {
        return DefaultBuilder()
    }
}
