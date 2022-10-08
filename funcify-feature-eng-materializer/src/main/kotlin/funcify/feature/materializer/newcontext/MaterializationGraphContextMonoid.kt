package funcify.feature.materializer.newcontext

import arrow.typeclasses.Monoid
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentSetValueMap
import graphql.language.OperationDefinition
import kotlinx.collections.immutable.PersistentMap

internal class MaterializationGraphContextMonoid(
    private val materializationMetamodel: MaterializationMetamodel,
    private val operationDefinition: OperationDefinition,
    private val materializationGraphContextFactory: MaterializationGraphContextFactory
) : Monoid<MaterializationGraphContext> {

    override fun empty(): MaterializationGraphContext {
        return materializationGraphContextFactory
            .builder()
            .materializationMetamodel(materializationMetamodel)
            .operationDefinition(operationDefinition)
            .build()
    }

    override fun MaterializationGraphContext.combine(
        b: MaterializationGraphContext
    ): MaterializationGraphContext {
        return materializationGraphContextFactory
            .builder()
            .materializationMetamodel(chooseLatestMaterializationMetamodel(this, b))
            .operationDefinition(this.operationDefinition)
            .requestParameterGraph(
                PathBasedGraph.monoid<SchematicPath, SchematicVertex, RequestParameterEdge>()
                    .invoke(this.requestParameterGraph, b.requestParameterGraph)
            )
            .materializedParameterValuesByPath(
                this.materializedParameterValuesByPath.putAll(b.materializedParameterValuesByPath)
            )
            .parameterIndexPathsBySourceIndexPath(
                this.parameterIndexPathsBySourceIndexPath.combineWithPersistentSetValueMap(
                    b.parameterIndexPathsBySourceIndexPath
                )
            )
            .queryVariables(this.queryVariables.putAll(b.queryVariables))
            .retrievalFunctionSpecByTopSourceIndexPath(
                combineRetrievalSpecs(
                    this.retrievalFunctionSpecByTopSourceIndexPath,
                    b.retrievalFunctionSpecByTopSourceIndexPath
                )
            )
            .build()
    }

    private fun chooseLatestMaterializationMetamodel(
        a: MaterializationGraphContext,
        b: MaterializationGraphContext
    ): MaterializationMetamodel {
        return when {
            a.materializationMetamodel.created < b.materializationMetamodel.created ->
                b.materializationMetamodel
            else -> a.materializationMetamodel
        }
    }

    private fun combineRetrievalSpecs(
        a: PersistentMap<SchematicPath, RetrievalFunctionSpec>,
        b: PersistentMap<SchematicPath, RetrievalFunctionSpec>,
    ): PersistentMap<SchematicPath, RetrievalFunctionSpec> {
        TODO("Not yet implemented")
    }
}
