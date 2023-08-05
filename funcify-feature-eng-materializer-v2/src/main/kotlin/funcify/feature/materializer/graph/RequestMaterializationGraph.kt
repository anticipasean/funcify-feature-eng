package funcify.feature.materializer.graph

import funcify.feature.schema.path.GQLOperationPath
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
