package funcify.feature.materializer.graph

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.line.DirectedLine
import funcify.feature.materializer.context.document.TabularDocumentContext
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.PersistentSetExtensions.toImmutableSet
import funcify.feature.tools.extensions.StreamExtensions.recurseBreadthFirst
import funcify.feature.tools.extensions.StreamExtensions.singleValueMapCombinationsFromPairs
import graphql.execution.preparsed.PreparsedDocumentEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.stream.Stream

/**
 * @author smccarron
 * @created 2023-09-21
 */
internal data class DefaultRequestMaterializationGraph(
    override val operationName: Option<String>,
    override val preparsedDocumentEntry: PreparsedDocumentEntry,
    override val requestGraph:
        DirectedPersistentGraph<GQLOperationPath, QueryComponentContext, MaterializationEdge>,
    override val passThruColumns: ImmutableSet<String>,
    override val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>,
    override val dataElementCallablesByPath: ImmutableMap<GQLOperationPath, DataElementCallable>,
    override val featureJsonValueStoreByPath: ImmutableMap<GQLOperationPath, FeatureJsonValueStore>,
    override val featureCalculatorCallablesByPath:
        ImmutableMap<GQLOperationPath, FeatureCalculatorCallable>,
    override val featureJsonValuePublisherByPath:
        ImmutableMap<GQLOperationPath, FeatureJsonValuePublisher>,
    override val lastUpdatedDataElementPathsByDataElementPath:
        ImmutableMap<GQLOperationPath, GQLOperationPath>,
    override val tabularDocumentContext: Option<TabularDocumentContext>,
    override val processingError: Option<ServiceError>,
) : RequestMaterializationGraph {

    override val featureArgumentGroupsByPath:
        (GQLOperationPath) -> ImmutableList<ImmutableMap<String, GQLOperationPath>> by lazy {
        val cache:
            ConcurrentMap<GQLOperationPath, ImmutableList<ImmutableMap<String, GQLOperationPath>>> =
            ConcurrentHashMap()
        val dependentArgsGraphCalculation:
            (GQLOperationPath) -> ImmutableList<ImmutableMap<String, GQLOperationPath>> =
            { path: GQLOperationPath ->
                Stream.of(path)
                    .filter { p: GQLOperationPath -> p in featureCalculatorCallablesByPath }
                    .flatMap { p: GQLOperationPath ->
                        requestGraph.edgesFromPointAsStream(p).flatMap {
                            (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                            requestGraph
                                .get(l.destinationPoint)
                                .toOption()
                                .filterIsInstance<ArgumentComponentContext>()
                                .map { facc: ArgumentComponentContext ->
                                    facc.argument.name to l.destinationPoint
                                }
                                .stream()
                        }
                    }
                    .singleValueMapCombinationsFromPairs()
                    .map(Map<String, GQLOperationPath>::toPersistentMap)
                    .reduceToPersistentList()
            }
        { path: GQLOperationPath ->
            cache.computeIfAbsent(path, dependentArgsGraphCalculation) ?: persistentListOf()
        }
    }

    override val featureArgumentDependenciesSetByPathAndIndex:
        (GQLOperationPath, Int) -> ImmutableSet<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<Pair<GQLOperationPath, Int>, ImmutableSet<GQLOperationPath>> =
            ConcurrentHashMap()
        //        val logger: Logger =
        // LoggerFactory.getLogger(DefaultRequestMaterializationGraph::class.java)
        val dependentValuePathsCalculation:
            (Pair<GQLOperationPath, Int>) -> ImmutableSet<GQLOperationPath> =
            { (path: GQLOperationPath, index: Int) ->
                // logger.debug(
                //    "feature_argument_dependencies_set_by_path_and_index: [ path: {}, index: {}
                // ]",
                //    path,
                //    index
                // )
                Stream.of(featureArgumentGroupsByPath.invoke(path))
                    .map { argGroups: ImmutableList<ImmutableMap<String, GQLOperationPath>> ->
                        // logger.debug(
                        //    "feature_argument_dependencies_set_by_path_and_index: [ path: {},
                        // arg_groups: {} ]",
                        //    path,
                        //    argGroups
                        // )
                        when {
                            index in argGroups.indices -> {
                                argGroups.get(index)
                            }
                            else -> {
                                persistentMapOf()
                            }
                        }
                    }
                    .map(ImmutableMap<String, GQLOperationPath>::entries)
                    .flatMap(ImmutableSet<Map.Entry<String, GQLOperationPath>>::stream)
                    .map(Map.Entry<String, GQLOperationPath>::value)
                    .recurseBreadthFirst { p: GQLOperationPath ->
                        // logger.debug(
                        //    "feature_argument_dependencies_set_by_path_and_index: [ path: {},
                        // recurse_on_path: {} ]",
                        //    path,
                        //    p
                        // )
                        // logger.debug("edges from point: [ path: {}, edges: {} ]", p,
                        // requestGraph.edgesFromPointAsStream(p).toList())
                        requestGraph.edgesFromPointAsStream(p).flatMap {
                            (l: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                            when (e) {
                                MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED,
                                MaterializationEdge.VARIABLE_VALUE_PROVIDED,
                                MaterializationEdge.RAW_INPUT_VALUE_PROVIDED -> {
                                    Stream.of(l.destinationPoint.right())
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
                    .toImmutableSet()
                // .also { depArgs ->
                //    logger.debug(
                //        "feature_argument_dependencies_set_by_path_and_index: [ path: {},
                // dep_args: {} ]",
                //        path,
                //        depArgs
                //    )
                // }
            }
        { path: GQLOperationPath, argumentGroupIndex: Int ->
            cache.computeIfAbsent(path to argumentGroupIndex, dependentValuePathsCalculation)
                ?: persistentSetOf()
        }
    }
}
