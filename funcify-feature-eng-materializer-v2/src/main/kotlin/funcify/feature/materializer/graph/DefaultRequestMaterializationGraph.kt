package funcify.feature.materializer.graph

import arrow.core.filterIsInstance
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.line.DirectedLine
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.PersistentSetExtensions.toImmutableSet
import funcify.feature.tools.extensions.StreamExtensions.recurse
import funcify.feature.tools.extensions.StreamExtensions.singleValueMapCombinationsFromPairs
import graphql.language.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * @author smccarron
 * @created 2023-09-21
 */
internal data class DefaultRequestMaterializationGraph(
    override val document: Document,
    override val requestGraph:
        DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>,
    override val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>,
    override val dataElementCallablesByPath: ImmutableMap<GQLOperationPath, DataElementCallable>,
    override val featureJsonValueStoreByPath: ImmutableMap<GQLOperationPath, FeatureJsonValueStore>,
    override val featureCalculatorCallablesByPath:
        ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>,
    override val featureJsonValuePublisherByPath:
        ImmutableMap<GQLOperationPath, FeatureJsonValuePublisher>,
) : RequestMaterializationGraph {

    override val featureDependentArgumentsByPath:
        (GQLOperationPath) -> ImmutableSet<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, ImmutableSet<GQLOperationPath>> =
            ConcurrentHashMap()
        val dependentArgsGraphCalculation: (GQLOperationPath) -> ImmutableSet<GQLOperationPath> =
            { path: GQLOperationPath ->
                Stream.of(path)
                    .recurse { p: GQLOperationPath ->
                        requestGraph.edgesFromPointAsStream(p).flatMap {
                            (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                            when (e) {
                                MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED,
                                MaterializationEdge.VARIABLE_VALUE_PROVIDED,
                                MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                                    Stream.of((l to e).right())
                                }
                                MaterializationEdge.EXTRACT_FROM_SOURCE -> {
                                    Stream.of(l.destinationPoint.left())
                                }
                                else -> {
                                    Stream.empty()
                                }
                            }
                        }
                    }
                    .flatMap { (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        // TODO: Add materialized raw_input or variable values to context
                        // so that same value may be extracted here from context
                        when (e) {
                            MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED,
                            MaterializationEdge.VARIABLE_VALUE_PROVIDED,
                            MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                                Stream.of(l.sourcePoint).filter { p: GQLOperationPath ->
                                    p.argumentReferent()
                                }
                            }
                            else -> {
                                Stream.empty()
                            }
                        }
                    }
                    .toImmutableSet()
            }
        { path: GQLOperationPath ->
            cache.computeIfAbsent(path, dependentArgsGraphCalculation) ?: persistentSetOf()
        }
    }

    val featureToArgumentGroups:
        (GQLOperationPath) -> ImmutableList<ImmutableSet<GQLOperationPath>> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, ImmutableList<ImmutableSet<GQLOperationPath>>> =
            ConcurrentHashMap()
        val dependentArgsGraphCalculation:
            (GQLOperationPath) -> ImmutableList<ImmutableSet<GQLOperationPath>> =
            { path: GQLOperationPath ->
                Stream.of(path)
                    .flatMap { p: GQLOperationPath ->
                        requestGraph.edgesFromPointAsStream(p).flatMap {
                            (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                            requestGraph
                                .get(l.destinationPoint)
                                .toOption()
                                .filterIsInstance<FieldArgumentComponentContext>()
                                .map { facc: FieldArgumentComponentContext ->
                                    facc.argument.name to l.destinationPoint
                                }
                                .stream()
                        }
                    }
                    .singleValueMapCombinationsFromPairs().map { m: Map<String, GQLOperationPath> ->
                        m.values.toImmutableSet()
                    }.reduceToPersistentList()
            }
        { path: GQLOperationPath ->
            cache.computeIfAbsent(path, dependentArgsGraphCalculation) ?: persistentListOf()
        }
    }

    override fun update(
        transformer: RequestMaterializationGraph.Builder.() -> RequestMaterializationGraph.Builder
    ): RequestMaterializationGraph {
        TODO("Not yet implemented")
    }
}
