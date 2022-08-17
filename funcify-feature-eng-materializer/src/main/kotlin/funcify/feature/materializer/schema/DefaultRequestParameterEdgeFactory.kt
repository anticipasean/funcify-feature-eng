package funcify.feature.materializer.schema

import arrow.core.Either
import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunction
import funcify.feature.materializer.schema.RequestParameterEdge.Builder
import funcify.feature.materializer.schema.RequestParameterEdge.DependentValueRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.MaterializedValueRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge.SpecBuilder
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionValueRequestParameterEdge
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
internal class DefaultRequestParameterEdgeFactory : RequestParameterEdgeFactory {

    companion object {

        internal class DefaultMaterializedValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val materializedJsonValue: JsonNode
        ) : MaterializedValueRequestParameterEdge {

            override fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultMissingContextValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>
        ) : RequestParameterEdge.MissingContextValueRequestParameterEdge {
            override fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultRetrievalFunctionSpecRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val dataSource: DataSource<*>,
            override val sourceVerticesByPath:
                PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentMapOf(),
            override val parameterVerticesByPath:
                PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentMapOf(),
        ) : RetrievalFunctionSpecRequestParameterEdge {

            override fun updateSpec(
                transformer: SpecBuilder.() -> SpecBuilder
            ): RetrievalFunctionSpecRequestParameterEdge {
                TODO("Not yet implemented")
            }

            override fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultDependentValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val extractionFunction:
                (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
        ) : DependentValueRequestParameterEdge {

            override fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultRetrievalFunctionValueRequestParameterEdge(
            override val id: Pair<SchematicPath, SchematicPath>,
            override val retrievalFunction: SchematicPathBasedJsonRetrievalFunction,
        ) : RetrievalFunctionValueRequestParameterEdge {

            override fun updateEdge(transformer: Builder.() -> Builder): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }

        internal class DefaultBuilder(
            var pathPair: Pair<SchematicPath, SchematicPath>? = null,
            var materializedJsonNode: JsonNode? = null,
            var dataSource: DataSource<*>? = null,
            var specBuilderFunction: (SpecBuilder.() -> SpecBuilder)? = null,
            var retrievalFunction: SchematicPathBasedJsonRetrievalFunction? = null,
            var extractor: ((ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>)? = null
        ) : Builder {

            override fun fromPathToPath(path1: SchematicPath, path2: SchematicPath): Builder {
                this.pathPair = path1 to path2
                return this
            }

            override fun materializedValue(materializedJsonNode: JsonNode): Builder {
                TODO("Not yet implemented")
            }

            override fun missingContextValue(): Builder {
                TODO("Not yet implemented")
            }

            override fun retrievalFunctionSpecForDataSource(
                dataSource: DataSource<*>,
                specCreator: SpecBuilder.() -> SpecBuilder,
            ): Builder {
                TODO("Not yet implemented")
            }

            override fun retrievalFunction(
                retrievalFunction: SchematicPathBasedJsonRetrievalFunction
            ): Builder {
                TODO("Not yet implemented")
            }

            override fun extractionFromAncestorFunction(
                extractor: (ImmutableMap<SchematicPath, JsonNode>) -> Option<JsonNode>
            ): Builder {
                TODO("Not yet implemented")
            }

            override fun build(): RequestParameterEdge {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): Builder {
        TODO("Not yet implemented")
    }
}
