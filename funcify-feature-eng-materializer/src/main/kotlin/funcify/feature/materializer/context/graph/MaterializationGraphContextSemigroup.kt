package funcify.feature.materializer.context.graph

import arrow.typeclasses.Semigroup
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentSetValueMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap

internal object MaterializationGraphContextSemigroup : Semigroup<MaterializationGraphContext> {

    override fun MaterializationGraphContext.combine(
        b: MaterializationGraphContext
    ): MaterializationGraphContext {
        val a: MaterializationGraphContext = this@combine
        return a.update {
            materializationMetamodel(chooseLatestMaterializationMetamodel(a, b))
                .operationDefinition(a.operationDefinition)
                .requestParameterGraph(
                    PathBasedGraph.monoid<SchematicPath, SchematicVertex, RequestParameterEdge>()(
                        a.requestParameterGraph,
                        b.requestParameterGraph
                    )
                )
                .queryVariables(a.queryVariables.toPersistentMap().putAll(b.queryVariables))
                .materializedParameterValuesByPath(
                    a.materializedParameterValuesByPath
                        .toPersistentMap()
                        .putAll(b.materializedParameterValuesByPath)
                )
                .parameterIndexPathsBySourceIndexPath(
                    a.parameterIndexPathsBySourceIndexPath.combineWithPersistentSetValueMap(
                        b.parameterIndexPathsBySourceIndexPath
                    )
                )
                .retrievalFunctionSpecsByTopSourceIndexPath(
                    combineRetrievalSpecs(
                        a.retrievalFunctionSpecByTopSourceIndexPath,
                        b.retrievalFunctionSpecByTopSourceIndexPath
                    )
                )
        }
    }

    private fun chooseLatestMaterializationMetamodel(
        a: MaterializationGraphContext,
        b: MaterializationGraphContext
    ): MaterializationMetamodel {
        return when {
            a.materializationMetamodel.created < b.materializationMetamodel.created -> {
                b.materializationMetamodel
            }
            else -> {
                a.materializationMetamodel
            }
        }
    }

    private fun combineRetrievalSpecs(
        a: PersistentMap<SchematicPath, RetrievalFunctionSpec>,
        b: PersistentMap<SchematicPath, RetrievalFunctionSpec>,
    ): PersistentMap<SchematicPath, RetrievalFunctionSpec> {
        return a.asSequence().fold(b) { pm, (k, v) ->
            if (pm.containsKey(k)) {
                val specBuilderUpdater:
                    (RetrievalFunctionSpec.SpecBuilder) -> RetrievalFunctionSpec.SpecBuilder =
                    { specBuilder ->
                        v.sourceVerticesByPath.asSequence().fold(
                            v.parameterVerticesByPath.asSequence().fold(specBuilder) {
                                sb,
                                (sp, pjvOrPlv) ->
                                pjvOrPlv.fold(
                                    { pjv -> sb.addParameterVertex(pjv) },
                                    { plv -> sb.addParameterVertex(plv) }
                                )
                            }
                        ) { sb, (sp, sjvOrSlv) ->
                            sjvOrSlv.fold(
                                { sjv -> sb.addSourceVertex(sjv) },
                                { slv -> sb.addSourceVertex(slv) }
                            )
                        }
                    }
                pm.put(k, pm[k]?.updateSpec(specBuilderUpdater) ?: v)
            } else {
                pm.put(k, v)
            }
        }
    }
}
