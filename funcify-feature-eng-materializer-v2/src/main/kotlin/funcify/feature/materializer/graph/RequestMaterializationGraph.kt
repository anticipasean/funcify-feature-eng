package funcify.feature.materializer.graph

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeaturePublisherCallable
import funcify.feature.schema.feature.FeatureStoreCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.language.Document
import graphql.language.Node
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface RequestMaterializationGraph {

    val document: Document

    val requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>

    val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>

    val dataElementCallablesByPath: ImmutableMap<GQLOperationPath, DataElementCallable>

    val featureStoreCallablesByPath: ImmutableMap<GQLOperationPath, FeatureStoreCallable>

    val featureCalculatorCallablesByPath: ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>

    val featurePublisherCallablesByPath: ImmutableMap<GQLOperationPath, FeaturePublisherCallable>

    fun update(transformer: Builder.() -> Builder): RequestMaterializationGraph

    interface Builder {

        fun document(document: Document): Builder

        fun requestGraph(
            requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>
        ): Builder

        fun addTransformerCallable(
            path: GQLOperationPath,
            transformerCallable: TransformerCallable
        ): Builder

        fun addDataElementCallable(
            path: GQLOperationPath,
            dataElementCallable: DataElementCallable
        ): Builder

        fun addFeatureStoreCallable(
            path: GQLOperationPath,
            featureStoreCallable: FeatureStoreCallable
        ): Builder

        fun addFeatureCalculatorCallable(
            path: GQLOperationPath,
            featureCalculatorCallable: FeatureCalculatorCallable
        ): Builder

        fun addFeaturePublisherCallable(
            path: GQLOperationPath,
            featurePublisherCallable: FeaturePublisherCallable
        ): Builder

        fun build(): RequestMaterializationGraph
    }
}
