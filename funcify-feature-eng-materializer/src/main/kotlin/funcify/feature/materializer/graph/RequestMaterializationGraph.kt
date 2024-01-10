package funcify.feature.materializer.graph

import arrow.core.Option
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import graphql.execution.preparsed.PreparsedDocumentEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface RequestMaterializationGraph {

    val operationName: Option<String>

    val preparsedDocumentEntry: PreparsedDocumentEntry

    val requestGraph:
        DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>

    val passThruColumns: ImmutableSet<String>

    val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>

    val dataElementCallablesByPath: ImmutableMap<GQLOperationPath, DataElementCallable>

    val featureJsonValueStoreByPath: ImmutableMap<GQLOperationPath, FeatureJsonValueStore>

    val featureCalculatorCallablesByPath: ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>

    val featureJsonValuePublisherByPath: ImmutableMap<GQLOperationPath, FeatureJsonValuePublisher>

    val lastUpdatedDataElementPathsByDataElementPath: ImmutableMap<GQLOperationPath, GQLOperationPath>

    val featureArgumentGroupsByPath:
        (GQLOperationPath) -> ImmutableList<ImmutableMap<String, GQLOperationPath>>

    val featureArgumentDependenciesSetByPathAndIndex:
        (GQLOperationPath, Int) -> ImmutableSet<GQLOperationPath>

    val processingError: Option<ServiceError>
}
