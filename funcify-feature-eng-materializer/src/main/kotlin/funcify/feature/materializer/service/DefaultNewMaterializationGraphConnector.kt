package funcify.feature.materializer.service

import arrow.core.*
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.context.MaterializationGraphVertexContext
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.newcontext.MaterializationGraphContext
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.schema.edge.RequestParameterEdgeFactory
import funcify.feature.materializer.schema.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-10-09
 */
class DefaultNewMaterializationGraphConnector(
    private val jsonMapper: JsonMapper,
    private val requestParameterEdgeFactory: RequestParameterEdgeFactory,
) : NewMaterializationGraphConnector {

    companion object {
        private val logger: Logger = loggerFor<DefaultNewMaterializationGraphConnector>()
    }
    override fun connectSourceRootVertex(
        vertex: SourceRootVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info("connect_source_root_vertex: [ vertex.path: {} ]", vertex.path)
        return context
    }

    override fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        field: Option<Field>,
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info(
            "connect_source_junction_or_leaf_vertex: [ field.name: {}, vertex.path: {} ]",
            field.map { f -> f.name }.getOrElse { "<NA>" },
            vertex.path
        )
        if (vertex.path in context.retrievalFunctionSpecByTopSourceIndexPath) {
            return context
        }
//        val selectedDatasource: DataSource<*> =
//            selectDataSourceForSourceAttributeVertex(vertex, context.metamodelGraph)
//        val currentOrAncestorPathWithSameDataSource =
//            findAncestorOrKeepCurrentWithSameDataSource(
//                vertex.path,
//                selectedDatasource.key,
//                context.metamodelGraph
//            )
//        val additionalEdgesUpdater:
//            (
//                MaterializationGraphVertexContext.Builder<V>
//            ) -> MaterializationGraphVertexContext.Builder<V> =
//            createAdditionalEdgesContextBuilderUpdaterForEntityIdentifiersAndLastUpdatedAttributeSupport(
//                context
//            )
//        // case 2.1: source_index does not have a retrieval_function_spec but does not
//        // have
//        // an ancestor that shares the same datasource and therefore must have its own
//        // retrieval_function_spec
//        // --> create retrieval_function_spec and connect any parameter_vertices associated
//        // with this spec
//        return if (currentOrAncestorPathWithSameDataSource == context.path) {
//            context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
//                .getOrNone(context.path)
//                .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
//                .fold(::emptySequence, ::identity)
//                .fold(
//                    context.update {
//                        addRetrievalFunctionSpecFor(sourceJunctionOrLeafVertex, selectedDatasource)
//                            .let { bldr -> additionalEdgesUpdater(bldr) }
//                    } as MaterializationGraphVertexContext<*>
//                ) { ctx, parameterAttributeVertex ->
//                    connectSchematicVertex(ctx.update { nextVertex(parameterAttributeVertex) })
//                }
//        } else {
//            // case 2.2: source_index does not have a retrieval_function_spec and does have
//            // an ancestor that shares the same datasource
//            // the value for this source_index therefore should be extracted from its
//            // ancestor result_map
//            // --> connect this vertex to its ancestor spec and connect any parameters
//            // associated with this vertex
//            context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
//                .getOrNone(context.path)
//                .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
//                .fold(::emptySequence, ::identity)
//                .fold(
//                    context.update {
//                        addRequestParameterEdge(
//                                requestParameterEdgeFactory
//                                    .builder()
//                                    .fromPathToPath(
//                                        currentOrAncestorPathWithSameDataSource,
//                                        context.path
//                                    )
//                                    .dependentExtractionFunction { resultMap ->
//                                        resultMap.getOrNone(
//                                            getVertexPathWithListIndexingIfDescendentOfListNode(
//                                                context.currentVertex,
//                                                context.graphQLSchema
//                                            )
//                                        )
//                                    }
//                                    .build()
//                            )
//                            .let { bldr -> additionalEdgesUpdater(bldr) }
//                    } as MaterializationGraphVertexContext<*>
//                ) { ctx, parameterAttributeVertex ->
//                    connectSchematicVertex(ctx.update { nextVertex(parameterAttributeVertex) })
//                }
//        }
        TODO()
    }

//    private fun <
//        V : SourceAttributeVertex
//    > createAdditionalEdgesContextBuilderUpdaterForEntityIdentifiersAndLastUpdatedAttributeSupport(
//        context: MaterializationGraphContext
//    ): (MaterializationGraphContext.Builder) -> MaterializationGraphContext.Builder {
//        val nearestLastUpdatedAttributeEdge: Option<RequestParameterEdge> =
//            createNearestLastUpdatedAttributeEdgeIfNotPresentInExpectedAncestorSpec(context)
//        val nearestIdentifierAttributeEdges: Sequence<RequestParameterEdge> =
//            createNearestIdentifierAttributeEdgesIfNotPresentInExpectedAncestorSpec(context)
//        return { builder: MaterializationGraphContext.Builder ->
//            nearestIdentifierAttributeEdges
//                .plus(nearestLastUpdatedAttributeEdge.fold(::emptySequence, ::sequenceOf))
//                .fold(builder) { bldr, re -> bldr.addRequestParameterEdge(re) }
//        }
//    }

    private fun selectDataSourceForSourceAttributeVertex(
        sourceAttributeVertex: SourceAttributeVertex,
        metamodelGraph: MetamodelGraph
    ): DataSource<*> {
        val topLevelDataSourceKeyForVertex: DataSource.Key<*> =
            sourceAttributeVertex.compositeAttribute
                .getSourceAttributeByDataSource()
                .keys
                .singleOrNone()
                .successIfDefined(
                    moreThanOneDataSourceFoundExceptionSupplier(sourceAttributeVertex.path)
                )
                .orElseThrow()
        return metamodelGraph.dataSourcesByKey[topLevelDataSourceKeyForVertex]
            .toOption()
            .successIfDefined(
                dataSourceNotFoundExceptionSupplier(
                    topLevelDataSourceKeyForVertex,
                    metamodelGraph.dataSourcesByKey.keys.toPersistentSet()
                )
            )
            .orElseThrow()
    }

    private fun findAncestorOrKeepCurrentWithSameDataSource(
        currentPath: SchematicPath,
        dataSourceKey: DataSource.Key<*>,
        metamodelGraph: MetamodelGraph
    ): SchematicPath {
        return currentPath
            .some()
            .recurse { p ->
                if (
                    p.getParentPath()
                        .flatMap { pp -> metamodelGraph.pathBasedGraph.getVertex(pp) }
                        .filterIsInstance<SourceAttributeVertex>()
                        .map(SourceAttributeVertex::compositeAttribute)
                        .map(CompositeSourceAttribute::getSourceAttributeByDataSource)
                        .fold(::emptyMap, ::identity)
                        .containsKey(dataSourceKey)
                ) {
                    p.getParentPath().map { pp -> pp.left() }
                } else {
                    p.right().some()
                }
            }
            .getOrElse { currentPath }
    }

    private fun moreThanOneDataSourceFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """more than one data_source found for vertex: 
                    |[ vertex_path: ${vertexPath} ]; 
                    |currently unable to handle more than 
                    |one data_source for a source_vertex""".flatten()
            )
        }
    }

    private fun dataSourceNotFoundExceptionSupplier(
        dataSourceKey: DataSource.Key<*>,
        availableDataSourceKeys: ImmutableSet<DataSource.Key<*>>
    ): () -> MaterializerException {
        return { ->
            val dataSourceKeysAvailable =
                availableDataSourceKeys
                    .asSequence()
                    .joinToString(
                        separator = ", ",
                        prefix = "{ ",
                        postfix = " }",
                        transform = { d -> "${d.name}: ${d.dataSourceType}" }
                    )
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """no data_source found in metamodel mapping to 
                    |data_source.key: 
                    |[ expected: ${dataSourceKey}, 
                    |actual: ${dataSourceKeysAvailable} 
                    |]""".flatten()
            )
        }
    }

    // TODO: This operation and its results can be cached separately
    private fun <V : SourceAttributeVertex> getVertexPathWithListIndexingIfDescendentOfListNode(
        vertex: V,
        graphQLSchema: GraphQLSchema
    ): SchematicPath {
        return ListIndexedSchematicPathGraphQLSchemaBasedCalculator(vertex.path, graphQLSchema)
            .successIfDefined {
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """unable to calculate list-indexed version of path [ vertex.path: ${vertex.path} ]""".flatten()
                )
            }
            .orElseThrow()
    }

    private fun <
        V : SourceAttributeVertex
    > createNearestLastUpdatedAttributeEdgeIfNotPresentInExpectedAncestorSpec(
        vertex: V,
        context: MaterializationGraphContext
    ): Option<RequestParameterEdge> {
        return context.metamodelGraph.lastUpdatedTemporalAttributePathRegistry
            .findNearestLastUpdatedTemporalAttributePathRelative(vertex.path)
            .flatMap { lastUpdatedRelativePath ->
                context.metamodelGraph.pathBasedGraph.getVertex(lastUpdatedRelativePath)
            }
            .filterIsInstance<SourceAttributeVertex>()
            .flatMap { sav: SourceAttributeVertex ->
                sav.compositeAttribute
                    .getSourceAttributeByDataSource()
                    .keys
                    .singleOrNone()
                    .map { dsKey ->
                        findAncestorOrKeepCurrentWithSameDataSource(
                            sav.path,
                            dsKey,
                            context.metamodelGraph
                        )
                    }
                    .filter { ancestorOrCurrentPath -> ancestorOrCurrentPath != sav.path }
                    .flatMap { ancestorPath ->
                        context.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(ancestorPath)
                            .filter { spec -> !spec.sourceVerticesByPath.containsKey(sav.path) }
                            .map { spec -> ancestorPath to spec }
                    }
                    .map { (ancestorPath, _) ->
                        requestParameterEdgeFactory
                            .builder()
                            .fromPathToPath(ancestorPath, sav.path)
                            .dependentExtractionFunction { resultMap ->
                                resultMap.getOrNone(
                                    getVertexPathWithListIndexingIfDescendentOfListNode(
                                        sav,
                                        context.graphQLSchema
                                    )
                                )
                            }
                            .build()
                    }
            }
    }

    private fun <
        V : SourceAttributeVertex
    > createNearestIdentifierAttributeEdgesIfNotPresentInExpectedAncestorSpec(
        vertex: V,
        context: MaterializationGraphContext
    ): Sequence<RequestParameterEdge> {
        return context.metamodelGraph.entityRegistry
            .findNearestEntityIdentifierPathRelatives(vertex.path)
            .asSequence()
            .flatMap { entityIdentifierPath ->
                context.metamodelGraph.pathBasedGraph
                    .getVertex(entityIdentifierPath)
                    .fold(::emptySequence, ::sequenceOf)
            }
            .filterIsInstance<SourceAttributeVertex>()
            .map { sav: SourceAttributeVertex ->
                sav.compositeAttribute
                    .getSourceAttributeByDataSource()
                    .keys
                    .singleOrNone()
                    .map { dsKey ->
                        findAncestorOrKeepCurrentWithSameDataSource(
                            sav.path,
                            dsKey,
                            context.metamodelGraph
                        )
                    }
                    .filter { ancestorOrCurrentPath -> ancestorOrCurrentPath != sav.path }
                    .flatMap { ancestorPath ->
                        context.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(ancestorPath)
                            .filter { spec -> !spec.sourceVerticesByPath.containsKey(sav.path) }
                            .map { spec -> ancestorPath to spec }
                    }
                    .map { (ancestorPath, _) ->
                        requestParameterEdgeFactory
                            .builder()
                            .fromPathToPath(ancestorPath, sav.path)
                            .dependentExtractionFunction { resultMap ->
                                resultMap.getOrNone(
                                    getVertexPathWithListIndexingIfDescendentOfListNode(
                                        sav,
                                        context.graphQLSchema
                                    )
                                )
                            }
                            .build()
                    }
            }
            .flatMapOptions()
    }

//    private fun addRequestParameterEdge(
//        requestParameterEdge: RequestParameterEdge,
//        context: MaterializationGraphContext
//    ): MaterializationGraphContext {
//        return requestParameterEdge.id.first
//            .toOption()
//            .flatMap { sp -> context.metamodelGraph.pathBasedGraph.getVertex(sp) }
//            .zip(
//                requestParameterEdge.id.second.toOption().flatMap { sp ->
//                    context.metamodelGraph.pathBasedGraph.getVertex(sp)
//                }
//            )
//            .map { (startVertex, endVertex) ->
//                updateRetrievalSpecsAndParameterTrackersPerVerticesAdded(startVertex, endVertex)
//                when (requestParameterEdge) {
//                    is RequestParameterEdge.MaterializedValueRequestParameterEdge -> {
//                        if (requestParameterEdge.id.first.arguments.isNotEmpty()) {
//                            context.materializedParameterValuesByPath.put(
//                                requestParameterEdge.id.first,
//                                requestParameterEdge.materializedJsonValue
//                            )
//                        }
//                        context.requestParameterGraph
//                            .putVertex(startVertex, SchematicVertex::path)
//                            .putVertex(endVertex, SchematicVertex::path)
//                            .putEdge(requestParameterEdge, RequestParameterEdge::id)
//                    }
//                    is RequestParameterEdge.DependentValueRequestParameterEdge -> {
//                        context.requestParameterGraph
//                            .putVertex(startVertex, SchematicVertex::path)
//                            .putVertex(endVertex, SchematicVertex::path)
//                            .putEdge(requestParameterEdge, RequestParameterEdge::id)
//                    }
//                    else -> {
//                        throw MaterializerException(
//                            MaterializerErrorResponse.UNEXPECTED_ERROR,
//                            """unhandled request_parameter_edge type:
//                            |[ type: ${requestParameterEdge::class.simpleName} ]
//                            |""".flatten()
//                        )
//                    }
//                }
//            }
//            .getOrElse { context.requestParameterGraph }
//    }

//    private fun updateRetrievalSpecsAndParameterTrackersPerVerticesAdded(
//        startVertex: SchematicVertex,
//        endVertex: SchematicVertex,
//        context: MaterializationGraphContext
//    ): MaterializationGraphContext {
//        sequenceOf(startVertex, endVertex).forEach { vertex: SchematicVertex ->
//            when (vertex) {
//                is SourceAttributeVertex -> {
//                    val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex> =
//                        when (vertex) {
//                            is SourceJunctionVertex -> vertex.left()
//                            is SourceLeafVertex -> vertex.right()
//                            else -> null
//                        }!!
//                    sourceJunctionOrLeafVertex
//                        .fold(
//                            SourceJunctionVertex::compositeAttribute,
//                            SourceLeafVertex::compositeAttribute
//                        )
//                        .getSourceAttributeByDataSource()
//                        .keys
//                        .asSequence()
//                        .map { dsKey ->
//                            dsKey to
//                                findAncestorOrKeepCurrentWithSameDataSource(
//                                    vertex.path,
//                                    dsKey,
//                                    context.metamodelGraph
//                                )
//                        }
//                        .map { (dsKey, ancestorOrCurrentPath) ->
//                            context.retrievalFunctionSpecByTopSourceIndexPath
//                                .getOrNone(ancestorOrCurrentPath)
//                                .filter { spec -> spec.dataSource.key == dsKey }
//                                .map { spec -> ancestorOrCurrentPath to spec }
//                        }
//                        .flatMapOptions()
//                        .firstOrNull()
//                        .toOption()
//                        .fold(
//                            {},
//                            { (sourceIndexPath, spec) ->
//                                context.retrievalFunctionSpecByTopSourceIndexPath.put(
//                                    sourceIndexPath,
//                                    spec.updateSpec {
//                                        sourceJunctionOrLeafVertex.fold(
//                                            { sjv -> addSourceVertex(sjv) },
//                                            { slv -> addSourceVertex(slv) }
//                                        )
//                                    }
//                                )
//                            }
//                        )
//                }
//                is ParameterAttributeVertex -> {
//                    val sourceIndexPath =
//                        vertex.path.transform { clearArguments().clearDirectives() }
//                    val parameterJunctionOrLeafVertex:
//                        Either<ParameterJunctionVertex, ParameterLeafVertex> =
//                        when (vertex) {
//                            is ParameterJunctionVertex -> vertex.left()
//                            is ParameterLeafVertex -> vertex.right()
//                            else -> null
//                        }!!
//                    when {
//                        // case 1: source_index corresponding to this parameter_index
//                        // already
//                        // has a retreival_function_spec
//                        // --> add this parameter_index to that spec
//                        sourceIndexPath in context.retrievalFunctionSpecByTopSourceIndexPath -> {
//                            context.retrievalFunctionSpecByTopSourceIndexPath.put(
//                                sourceIndexPath,
//                                context.retrievalFunctionSpecByTopSourceIndexPath
//                                    .getOrNone(sourceIndexPath)
//                                    .map { spec ->
//                                        spec.updateSpec {
//                                            parameterJunctionOrLeafVertex.fold(
//                                                { pjv -> addParameterVertex(pjv) },
//                                                { plv -> addParameterVertex(plv) }
//                                            )
//                                        }
//                                    } /* already assessed that spec is defined so non-null assertion ok */
//                                    .orNull()!!
//                            )
//                        }
//                        // case 2: source_index corresponding to this parameter_index does
//                        // not
//                        // already have a retrieval function spec associated
//                        // --> create the retrieval spec for this source_index and add this
//                        // parameter_index to it
//                        else -> {
//                            context.metamodelGraph.pathBasedGraph
//                                .getVertex(sourceIndexPath)
//                                .filterIsInstance<SourceAttributeVertex>()
//                                .flatMap { sav ->
//                                    sav.compositeAttribute
//                                        .getSourceAttributeByDataSource()
//                                        .keys
//                                        .asSequence()
//                                        .map { key ->
//                                            context.metamodelGraph.dataSourcesByKey.getOrNone(key)
//                                        }
//                                        .flatMapOptions()
//                                        .firstOrNull()
//                                        .toOption()
//                                        .map { ds -> sav to ds }
//                                }
//                                .flatMap { (sav, ds) ->
//                                    when (sav) {
//                                            is SourceJunctionVertex -> sav.left()
//                                            is SourceLeafVertex -> sav.right()
//                                            else -> null
//                                        }
//                                        .toOption()
//                                        .map { sjvOrSlv -> sjvOrSlv to ds }
//                                }
//                                .tap { (sjvOrSlv, ds) -> addRetrievalFunctionSpecFor(sjvOrSlv, ds) }
//
//                            context.retrievalFunctionSpecByTopSourceIndexPath
//                                .getOrNone(sourceIndexPath)
//                                .map { spec ->
//                                    spec.updateSpec {
//                                        parameterJunctionOrLeafVertex.fold(
//                                            { pjv -> addParameterVertex(pjv) },
//                                            { plv -> addParameterVertex(plv) }
//                                        )
//                                    }
//                                }
//                                .tap { updatedSpec ->
//                                    retrievalFunctionSpecByTopSourceIndexPath.put(
//                                        sourceIndexPath,
//                                        updatedSpec
//                                    )
//                                }
//                        }
//                    }
//                    context.parameterIndexPathsBySourceIndexPath.put(
//                        sourceIndexPath,
//                        context.parameterIndexPathsBySourceIndexPath
//                            .getOrDefault(sourceIndexPath, persistentSetOf())
//                            .add(vertex.path)
//                    )
//                }
//            }
//        }
//    }

    override fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        argument: Option<Argument>,
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info(
            "connect_parameter_junction_or_leaf_vertex: [ argument.name: {}, vertex.path: {} ]",
            argument.map { a -> a.name }.getOrElse { "<NA>" },
            vertex.path
        )
        TODO("Not yet implemented")
    }

    override fun connectVertices(
        vertex1: SchematicVertex,
        vertex2: SchematicVertex,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        TODO("Not yet implemented")
    }
}
