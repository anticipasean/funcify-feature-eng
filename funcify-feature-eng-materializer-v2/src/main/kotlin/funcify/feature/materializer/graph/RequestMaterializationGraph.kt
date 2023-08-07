package funcify.feature.materializer.graph

import funcify.feature.materializer.graph.callable.DataElementCallable
import funcify.feature.materializer.graph.callable.FeatureCalculatorCallable
import funcify.feature.materializer.graph.callable.FeaturePublisherCallable
import funcify.feature.materializer.graph.callable.FeatureStoreCallable
import funcify.feature.materializer.graph.callable.TransformerCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface RequestMaterializationGraph {

    val dataElementCallablesByPath: ImmutableMap<GQLOperationPath, DataElementCallable>

    val featureStoreCallablesByPath: ImmutableMap<GQLOperationPath, FeatureStoreCallable>

    val featureCalculatorCallablesByPath: ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>

    val featurePublisherCallablesByPath: ImmutableMap<GQLOperationPath, FeaturePublisherCallable>

    val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>
}
