package funcify.feature.materializer.newcontext

import arrow.core.continuations.eagerEffect
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.newcontext.MaterializationGraphContext.Builder
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-10-08
 */
internal class DefaultMaterializationGraphContextFactory : MaterializationGraphContextFactory {

    companion object {
        internal class DefaultMaterializationGraphContextBuilder(
            private var materializationMetamodel: MaterializationMetamodel? = null,
            private var operationDefinition: OperationDefinition? = null,
            private var queryVariables: PersistentMap<String, Any> = persistentMapOf(),
            private var requestParameterGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            private var materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode> =
                persistentMapOf(),
            private var parameterIndexPathsBySourceIndexPath:
                PersistentMap<SchematicPath, PersistentSet<SchematicPath>> =
                persistentMapOf(),
            private var retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf()
        ) : Builder {

            override fun materializationMetamodel(
                materializationMetamodel: MaterializationMetamodel
            ): Builder {
                this.materializationMetamodel = materializationMetamodel
                return this
            }

            override fun operationDefinition(operationDefinition: OperationDefinition): Builder {
                this.operationDefinition = operationDefinition
                return this
            }

            override fun queryVariables(queryVariables: PersistentMap<String, Any>): Builder {
                this.queryVariables = queryVariables
                return this
            }

            override fun requestParameterGraph(
                requestParameterGraph:
                    PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
            ): Builder {
                this.requestParameterGraph = requestParameterGraph
                return this
            }

            override fun materializedParameterValuesByPath(
                materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>
            ): Builder {
                this.materializedParameterValuesByPath = materializedParameterValuesByPath
                return this
            }

            override fun parameterIndexPathsBySourceIndexPath(
                parameterIndexPathsBySourceIndexPath:
                    PersistentMap<SchematicPath, PersistentSet<SchematicPath>>
            ): Builder {
                this.parameterIndexPathsBySourceIndexPath = parameterIndexPathsBySourceIndexPath
                return this
            }

            override fun retrievalFunctionSpecByTopSourceIndexPath(
                retrievalFunctionSpecByTopSourceIndexPath:
                    PersistentMap<SchematicPath, RetrievalFunctionSpec>
            ): Builder {
                this.retrievalFunctionSpecByTopSourceIndexPath =
                    retrievalFunctionSpecByTopSourceIndexPath
                return this
            }

            override fun build(): MaterializationGraphContext {
                return eagerEffect<String, MaterializationGraphContext> {
                        ensure(materializationMetamodel != null) {
                            "materialization_metamodel has not been provided"
                        }
                        ensure(operationDefinition != null) {
                            "operation_definition has not been provided"
                        }
                        DefaultMaterializationGraphContext(
                            materializationMetamodel!!,
                            operationDefinition!!,
                            queryVariables,
                            requestParameterGraph,
                            materializedParameterValuesByPath,
                            parameterIndexPathsBySourceIndexPath,
                            retrievalFunctionSpecByTopSourceIndexPath,
                        )
                    }
                    .fold(
                        { message ->
                            throw MaterializerException(
                                MaterializerErrorResponse.METAMODEL_GRAPH_CREATION_ERROR,
                                "materialization_graph_context could not be built: [ message: %s ]".format(
                                    message
                                )
                            )
                        },
                        { context -> context }
                    )
            }
        }

        internal data class DefaultMaterializationGraphContext(
            override val materializationMetamodel: MaterializationMetamodel,
            override val operationDefinition: OperationDefinition,
            override val queryVariables: PersistentMap<String, Any>,
            override val requestParameterGraph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
            override val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode>,
            override val parameterIndexPathsBySourceIndexPath:
                PersistentMap<SchematicPath, PersistentSet<SchematicPath>>,
            override val retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec>
        ) : MaterializationGraphContext {

            override fun update(transformer: Builder.() -> Builder): MaterializationGraphContext {
                return transformer(
                        DefaultMaterializationGraphContextBuilder(
                            materializationMetamodel = materializationMetamodel,
                            operationDefinition = operationDefinition,
                            queryVariables = queryVariables,
                            requestParameterGraph = requestParameterGraph,
                            materializedParameterValuesByPath = materializedParameterValuesByPath,
                            parameterIndexPathsBySourceIndexPath =
                                parameterIndexPathsBySourceIndexPath,
                            retrievalFunctionSpecByTopSourceIndexPath =
                                retrievalFunctionSpecByTopSourceIndexPath
                        )
                    )
                    .build()
            }
        }
    }

    override fun builder(): Builder {
        return DefaultMaterializationGraphContextBuilder()
    }
}
