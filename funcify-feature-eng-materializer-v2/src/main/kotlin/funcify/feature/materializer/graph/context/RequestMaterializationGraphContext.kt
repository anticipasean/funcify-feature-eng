package funcify.feature.materializer.graph.context

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSource
import graphql.language.Node
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface RequestMaterializationGraphContext {

    val materializationMetamodel: MaterializationMetamodel

    val variableKeys: ImmutableSet<String>

    val rawInputContextKeys: ImmutableSet<String>

    val requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>

    val passThroughColumns: ImmutableSet<String>

    val transformerCallableBuildersByPath: ImmutableMap<GQLOperationPath, TransformerSource.Builder>

    val dataElementCallableBuildersByPath: ImmutableMap<GQLOperationPath, DataElementSource.Builder>

    val featureCalculatorCallableBuildersByPath:
        ImmutableMap<GQLOperationPath, FeatureCalculator.Builder>

    val queryComponentContextFactory: QueryComponentContextFactory

    val addedVertexContexts: ImmutableList<QueryComponentContext>

    interface Builder<B : Builder<B>> {

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): B

        fun variableKeys(variableKeys: ImmutableSet<String>): B

        fun rawInputContextKeys(rawInputContextKeys: ImmutableSet<String>): B

        fun addPassThroughColumn(name: String): B

        fun requestGraph(
            requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>
        ): B

        fun putTransformerCallableBuildersForPath(
            path: GQLOperationPath,
            transformerCallableBuilder: TransformerSource.Builder
        ): B

        fun putDataElementCallableBuildersForPath(
            path: GQLOperationPath,
            dataElementCallableBuilder: DataElementSource.Builder
        ): B

        fun putFeatureCalculatorCallableBuildersForPath(
            path: GQLOperationPath,
            featureCalculatorCallableBuilder: FeatureCalculator.Builder
        ): B

        fun queryComponentContextFactory(
            queryComponentContextFactory: QueryComponentContextFactory
        ): B

        fun addVertexContext(nextVertex: QueryComponentContext): B

        fun addedVertexContexts(addedVertexContexts: Iterable<QueryComponentContext>): B

        fun dropFirstAddedVertex(): B
    }
}
