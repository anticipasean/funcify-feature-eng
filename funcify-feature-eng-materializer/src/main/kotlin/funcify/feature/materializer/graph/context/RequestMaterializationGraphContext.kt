package funcify.feature.materializer.graph.context

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContextFactory
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.document.GQLDocumentComposer
import funcify.feature.schema.document.GQLDocumentSpecFactory
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.schema.FieldCoordinates
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

    val requestGraph:
        DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>

    val passThroughColumns: ImmutableSet<String>

    val connectedFieldPathsByCoordinates:
        ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>

    val connectedPathsByCanonicalPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

    val canonicalPathByConnectedPath: ImmutableMap<GQLOperationPath, GQLOperationPath>

    val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>

    val dataElementCallableBuildersByPath:
        ImmutableMap<GQLOperationPath, DataElementCallable.Builder>

    val featureCalculatorCallablesByPath: ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>

    val featureJsonValueStoresByPath: ImmutableMap<GQLOperationPath, FeatureJsonValueStore>

    val featureJsonValuePublishersByPath: ImmutableMap<GQLOperationPath, FeatureJsonValuePublisher>

    val lastUpdatedDataElementPathsByDataElementPath:
        ImmutableMap<GQLOperationPath, GQLOperationPath>

    val queryComponentContextFactory: QueryComponentContextFactory

    val gqlDocumentSpecFactory: GQLDocumentSpecFactory

    val gqlDocumentComposer: GQLDocumentComposer

    interface Builder<B : Builder<B>> {

        fun materializationMetamodel(materializationMetamodel: MaterializationMetamodel): B

        fun variableKeys(variableKeys: ImmutableSet<String>): B

        fun rawInputContextKeys(rawInputContextKeys: ImmutableSet<String>): B

        fun addPassThroughColumn(name: String): B

        fun putConnectedFieldPathForCoordinates(
            fieldCoordinates: FieldCoordinates,
            path: GQLOperationPath
        ): B

        fun putConnectedPathForCanonicalPath(
            canonicalPath: GQLOperationPath,
            path: GQLOperationPath
        ): B

        fun requestGraph(
            requestGraph:
                DirectedPersistentGraph<
                    GQLOperationPath,
                    QueryComponentContext,
                    MaterializationEdge
                >
        ): B

        fun putTransformerCallableForPath(
            path: GQLOperationPath,
            transformerCallable: TransformerCallable
        ): B

        fun putDataElementCallableBuilderForPath(
            path: GQLOperationPath,
            dataElementCallableBuilder: DataElementCallable.Builder
        ): B

        fun putFeatureCalculatorCallableForPath(
            path: GQLOperationPath,
            featureCalculatorCallable: FeatureCalculatorCallable
        ): B

        fun putFeatureJsonValueStoreForPath(
            path: GQLOperationPath,
            featureJsonValueStore: FeatureJsonValueStore
        ): B

        fun putFeatureJsonValuePublisherForPath(
            path: GQLOperationPath,
            featureJsonValuePublisher: FeatureJsonValuePublisher
        ): B

        fun putLastUpdatedDataElementPathForDataElementPath(
            dataElementPath: GQLOperationPath,
            lastUpdatedDataElementPath: GQLOperationPath
        ): B

        fun queryComponentContextFactory(
            queryComponentContextFactory: QueryComponentContextFactory
        ): B

        fun gqlDocumentSpecFactory(gqlDocumentSpecFactory: GQLDocumentSpecFactory): B

        fun gqlDocumentComposer(gqlDocumentComposer: GQLDocumentComposer): B
    }
}
