package funcify.feature.materializer.service

import arrow.core.*
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.json.GraphQLValueToJsonNodeConverter
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdgeFactory
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.NullValue
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
internal class DefaultMaterializationGraphVertexConnector(
    private val requestParameterEdgeFactory: RequestParameterEdgeFactory,
) : MaterializationGraphVertexConnector {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphVertexConnector>()
    }

    override fun onSourceRootVertex(
        context: SourceRootMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_source_root_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return context.update {
            graph(context.graph.putVertex(SchematicPath.getRootPath(), context.currentVertex))
        }
    }

    override fun onSourceJunctionVertex(
        context: SourceJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_source_junction_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // TODO: can combine cases where top-level node and no ancestor_retrieval_function_spec:
            // both requiring assessment of data_source.key and creation of the
            // retrieval_function_spec edge
            context.path.isChildTo(SchematicPath.getRootPath()) &&
                context.path
                    .getParentPath()
                    .flatMap { pp ->
                        context.graph.getEdgesFromPathToPath(pp, context.path).firstOrNone()
                    }
                    .isDefined() -> {
                context
            }
            context.path.isChildTo(SchematicPath.getRootPath()) -> {
                val topLevelDataSourceForVertex: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(context.session, context.currentVertex)
                val edge =
                    requestParameterEdgeFactory
                        .builder()
                        .fromPathToPath(SchematicPath.getRootPath(), context.path)
                        .retrievalFunctionSpecForDataSource(topLevelDataSourceForVertex) {
                            addSourceVertex(context.currentVertex)
                        }
                        .build()
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(SchematicPath.getRootPath(), context.path, edge)
                    )
                }
            }
            else -> {
                val ancestorRetrievalFunctionSpec: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                val ancestorRetrievalSpecDataSourceKey: DataSource.Key<*> =
                    ancestorRetrievalFunctionSpec.dataSource.key
                when {
                    // case 1: source_junction_vertex has a representation in the same data_source
                    // as the one in the spec being constructed
                    context.currentVertex.compositeAttribute
                        .getSourceAttributeForDataSourceKey(ancestorRetrievalSpecDataSourceKey)
                        .toOption()
                        .isDefined() -> {
                        // add vertex to this spec
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(
                                        ancestorRetrievalFunctionSpec.id,
                                        ancestorRetrievalFunctionSpec.updateSpec {
                                            addSourceVertex(context.currentVertex)
                                        }
                                    )
                                    // add edge connecting parent to child indicating extraction
                                    // should be done on the ancestor result node
                                    .putEdge(
                                        context.parentPath.orNull()!!,
                                        context.path,
                                        requestParameterEdgeFactory
                                            .builder()
                                            .fromPathToPath(
                                                context.parentPath.orNull()!!,
                                                context.path
                                            )
                                            .extractionFromAncestorFunction { parentResultMap ->
                                                parentResultMap[context.path].toOption()
                                            }
                                            .build()
                                    )
                            )
                        }
                    }
                    // case 2: source_junction_vertex does not have a representation in the same
                    // ancestor_retrieval_function_spec so it cannot be fetched from the same
                    // data_source
                    // and requires its own retrieval spec
                    else -> {
                        val dataSourceForVertex: DataSource<*> =
                            selectDataSourceForSourceAttributeVertex(
                                context.session,
                                context.currentVertex
                            )
                        val edge =
                            requestParameterEdgeFactory
                                .builder()
                                .fromPathToPath(context.parentPath.orNull()!!, context.path)
                                .retrievalFunctionSpecForDataSource(dataSourceForVertex) {
                                    addSourceVertex(context.currentVertex)
                                }
                                .build()
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(context.parentPath.orNull()!!, context.path, edge)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun selectDataSourceForSourceAttributeVertex(
        session: SingleRequestFieldMaterializationSession,
        sourceAttributeVertex: SourceAttributeVertex
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
        return session.metamodelGraph.dataSourcesByKey[topLevelDataSourceKeyForVertex]
            .toOption()
            .successIfDefined(
                dataSourceNotFoundExceptionSupplier(
                    topLevelDataSourceKeyForVertex,
                    session.metamodelGraph.dataSourcesByKey.keys.toPersistentSet()
                )
            )
            .orElseThrow()
    }

    private fun findAncestorRetrievalFunctionSpecRequestParameterEdge(
        context: MaterializationGraphVertexContext<*>
    ): Try<RetrievalFunctionSpecRequestParameterEdge> {
        return findAncestorRetrievalFunctionSpecRequestParameterEdge(context.path, context.graph)
    }

    private fun findAncestorRetrievalFunctionSpecRequestParameterEdge(
        vertexPath: SchematicPath,
        graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
    ): Try<RetrievalFunctionSpecRequestParameterEdge> {
        return vertexPath
            .getParentPath()
            .recurse { pp ->
                when (
                    val edgeBetweenGrandparentAndParent: RequestParameterEdge? =
                        pp.getParentPath()
                            .flatMap { ppp -> graph.getEdgesFromPathToPath(ppp, pp).firstOrNone() }
                            .orNull()
                ) {
                    null -> {
                        none()
                    }
                    is RetrievalFunctionSpecRequestParameterEdge -> {
                        edgeBetweenGrandparentAndParent.right().some()
                    }
                    else -> {
                        pp.getParentPath().map { ppp -> ppp.left() }
                    }
                }
            }
            .successIfDefined(ancestorRetrievalFunctionSpecNotFoundExceptionSupplier(vertexPath))
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

    private fun ancestorRetrievalFunctionSpecNotFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """retrieval_function_spec not found in ancestor of 
                    |[ vertex_path: ${vertexPath} ]; 
                    |vertices are likely being 
                    |processed out-of-order""".flatten()
            )
        }
    }

    override fun onSourceLeafVertex(
        context: SourceLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_source_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // TODO: can combine cases where top-level node and no ancestor_retrieval_function_spec:
            // both requiring assessment of data_source.key and creation of the
            // retrieval_function_spec edge
            context.path.isChildTo(SchematicPath.getRootPath()) -> {
                val topLevelDataSourceForVertex: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(context.session, context.currentVertex)
                val edge =
                    requestParameterEdgeFactory
                        .builder()
                        .fromPathToPath(SchematicPath.getRootPath(), context.path)
                        .retrievalFunctionSpecForDataSource(topLevelDataSourceForVertex) {
                            addSourceVertex(context.currentVertex)
                        }
                        .build()
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(edge, RequestParameterEdge::id)
                    )
                }
            }
            else -> {
                val ancestorRetrievalFunctionSpec: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                val ancestorRetrievalSpecDataSourceKey: DataSource.Key<*> =
                    ancestorRetrievalFunctionSpec.dataSource.key
                when {
                    // case 1: source_junction_vertex has a representation in the same data_source
                    // as the one in the spec being constructed
                    context.currentVertex.compositeAttribute
                        .getSourceAttributeForDataSourceKey(ancestorRetrievalSpecDataSourceKey)
                        .toOption()
                        .isDefined() -> {
                        // add vertex to this spec
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(
                                        ancestorRetrievalFunctionSpec.updateSpec {
                                            addSourceVertex(context.currentVertex)
                                        },
                                        RequestParameterEdge::id
                                    )
                                    // add edge connecting parent to child indicating extraction
                                    // should be done on the ancestor result node
                                    .putEdge(
                                        requestParameterEdgeFactory
                                            .builder()
                                            .fromPathToPath(
                                                context.parentPath.orNull()!!,
                                                context.path
                                            )
                                            .extractionFromAncestorFunction { parentResultMap ->
                                                parentResultMap[context.path].toOption()
                                            }
                                            .build(),
                                        RequestParameterEdge::id
                                    )
                            )
                        }
                    }
                    // case 2: source_junction_vertex does not have a representation in the same
                    // ancestor_retrieval_function_spec so it cannot be fetched from the same
                    // data_source
                    // and requires its own retrieval spec
                    else -> {

                        val dataSourceForVertex: DataSource<*> =
                            selectDataSourceForSourceAttributeVertex(
                                context.session,
                                context.currentVertex
                            )
                        val edge =
                            requestParameterEdgeFactory
                                .builder()
                                .fromPathToPath(context.parentPath.orNull()!!, context.path)
                                .retrievalFunctionSpecForDataSource(dataSourceForVertex) {
                                    addSourceVertex(context.currentVertex)
                                }
                                .build()
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(edge, RequestParameterEdge::id)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_parameter_junction_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            !context.argument.value.toOption().filterIsInstance<NullValue>().isDefined() -> {
                // add materialized value as an edge from this parameter to its source_vertex and
                // add this parameter_path to the ancestor function spec so that it can be used in
                // the request made to the source
                val ancestorRetrievalFunctionSpecEdge: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdge.updateSpec {
                                    addParameterVertex(context.currentVertex)
                                },
                                RequestParameterEdge::id
                            )
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(context.parentPath.orNull()!!, context.path)
                                    .materializedValue(
                                        GraphQLValueToJsonNodeConverter.invoke(
                                                context.argument.value
                                            )
                                            .successIfDefined(
                                                argumentValueNotResolvedIntoJsonExceptionSupplier(
                                                    context.path,
                                                    context.argument
                                                )
                                            )
                                            .orElseThrow()
                                    )
                                    .build(),
                                RequestParameterEdge::id
                            )
                    )
                }
            }
            // case 2: caller has not provided an input value for this argument so it needs to be
            // retrieved through some other source--if possible
            else -> {
                val sourceAttributeVertexWithSameNameOrAlias =
                    findSourceAttributeVertexWithSameNameInSameDomain(context)
                        .or(findSourceAttributeVertexByAliasReferenceInSameDomain(context))
                        .or(findSourceAttributeVertexWithSameNameInDifferentDomain(context))
                        .or(findSourceAttributeVertexByAliasReferenceInDifferentDomain(context))
                when {
                    sourceAttributeVertexWithSameNameOrAlias.isDefined() -> {
                        getParameterAttributeVertexValueThroughSourceAttributeVertex(
                            sourceAttributeVertexWithSameNameOrAlias.orNull()!!,
                            context
                        )
                    }
                    else -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """parameter_junction_vertex could not be mapped to 
                                |source_attribute_vertex such that its materialized 
                                |value could be obtained: 
                                |[ vertex_path: ${context.path} 
                                |]""".flatten()
                        )
                    }
                }
            }
        }
    }

    private fun <
        V : ParameterAttributeVertex> getParameterAttributeVertexValueThroughSourceAttributeVertex(
        sourceAttributeVertex: SourceAttributeVertex,
        context: MaterializationGraphVertexContext<V>,
    ): MaterializationGraphVertexContext<*> {
        return when {
            context.graph.getVertex(sourceAttributeVertex.path).isDefined() -> {
                val sourceAttributeVertexPath: SchematicPath = sourceAttributeVertex.path
                val ancestorRetrievalFunctionSpecEdgeForSourceAttributeVertex =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(
                            sourceAttributeVertexPath,
                            context.graph
                        )
                        .orElseThrow()
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(
                                        ancestorRetrievalFunctionSpecEdgeForSourceAttributeVertex.id
                                            .second,
                                        context.path
                                    )
                                    .extractionFromAncestorFunction { jsonValuesMap ->
                                        jsonValuesMap.getOrNone(sourceAttributeVertexPath)
                                    }
                                    .build(),
                                RequestParameterEdge::id
                            )
                    )
                }
            }
            // Current ancestor retrieval function spec is for a datasource
            // not supported by this source_attribute_vertex
            findAncestorRetrievalFunctionSpecRequestParameterEdge(
                    sourceAttributeVertex.path,
                    context.graph
                )
                .getSuccess()
                .filter { edge ->
                    sourceAttributeVertex.compositeAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .none { dskey -> dskey == edge.dataSource.key }
                }
                .isDefined() -> {
                val (pathWithSpecNeeded, datasourceKey) =
                    assessOrFindPathAndDataSourceKeyForWhichToCreateRetrievalFunctionSpec(
                        sourceAttributeVertex,
                        context
                    )
                val dataSourceForKey: DataSource<*> =
                    context.session.metamodelGraph.dataSourcesByKey[datasourceKey]
                        .toOption()
                        .successIfDefined(
                            dataSourceNotFoundExceptionSupplier(
                                datasourceKey,
                                context.session.metamodelGraph.dataSourcesByKey.keys
                            )
                        )
                        .orElseThrow()
                val graphWithVerticesAdded:
                    PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
                    if (context.graph.getVertex(pathWithSpecNeeded).isDefined()) {
                        context.graph
                            .putVertex(sourceAttributeVertex, SchematicVertex::path)
                            .putVertex(context.currentVertex, SchematicVertex::path)
                    } else {
                        pathWithSpecNeeded
                            .getParentPath()
                            .flatMap { pp ->
                                context.session.metamodelGraph.pathBasedGraph.getVertex(pp)
                            }
                            .zip(
                                context.session.metamodelGraph.pathBasedGraph.getVertex(
                                    pathWithSpecNeeded
                                )
                            )
                            .map { (a1Vertex, a2Vertex) ->
                                // These could potentially be the same but it shouldn't
                                // be an issue as long as we're not removing vertices
                                // which could in turn remove edges
                                context.graph
                                    .putVertex(a1Vertex, SchematicVertex::path)
                                    .putVertex(a2Vertex, SchematicVertex::path)
                                    .putVertex(sourceAttributeVertex, SchematicVertex::path)
                                    .putVertex(context.currentVertex, SchematicVertex::path)
                            }
                            .successIfDefined(
                                oneOrMoreVerticesMissingFromMetamodelExceptionSupplier(
                                    pathWithSpecNeeded.getParentPath().getOrElse {
                                        SchematicPath.getRootPath()
                                    },
                                    pathWithSpecNeeded
                                )
                            )
                            .orElseThrow()
                    }

                val sourceOrJunctionVertex: Either<SourceJunctionVertex, SourceLeafVertex> =
                    when (sourceAttributeVertex) {
                        is SourceJunctionVertex -> sourceAttributeVertex.left()
                        is SourceLeafVertex -> sourceAttributeVertex.right()
                        else -> null
                    }!!

                context.update {
                    graph(
                        graphWithVerticesAdded
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(
                                        pathWithSpecNeeded.getParentPath().getOrElse {
                                            SchematicPath.getRootPath()
                                        },
                                        pathWithSpecNeeded
                                    )
                                    .retrievalFunctionSpecForDataSource(dataSourceForKey) {
                                        sourceOrJunctionVertex.fold(
                                            { sjv -> addSourceVertex(sjv) },
                                            { slv -> addSourceVertex(slv) }
                                        )
                                    }
                                    .build(),
                                RequestParameterEdge::id
                            )
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(pathWithSpecNeeded, context.path)
                                    .extractionFromAncestorFunction { resultJsonMap ->
                                        resultJsonMap.getOrNone(pathWithSpecNeeded)
                                    }
                                    .build(),
                                RequestParameterEdge::id
                            )
                    )
                }
            }
            else -> {
                val ancestorRetrievalFunctionSpecEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                // source_root_vertex is not of type
                // source_attribute_vertex
                // so only these two are available in the standard setup
                val sourceJunctionOrLeafVertex =
                    when (sourceAttributeVertex) {
                        is SourceJunctionVertex -> sourceAttributeVertex.left()
                        is SourceLeafVertex -> sourceAttributeVertex.right()
                        else -> null
                    }!!
                context.update {
                    graph(
                        context.graph
                            .putVertex(sourceAttributeVertex, SchematicVertex::path)
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdge.updateSpec {
                                    sourceJunctionOrLeafVertex.fold(
                                        { sjv -> addSourceVertex(sjv) },
                                        { slv -> addSourceVertex(slv) }
                                    )
                                },
                                RequestParameterEdge::id
                            )
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(
                                        ancestorRetrievalFunctionSpecEdge.id.second,
                                        context.path
                                    )
                                    .extractionFromAncestorFunction { valuesMap ->
                                        valuesMap.getOrNone(sourceAttributeVertex.path)
                                    }
                                    .build(),
                                RequestParameterEdge::id
                            )
                    )
                }
            }
        }
    }

    private fun oneOrMoreVerticesMissingFromMetamodelExceptionSupplier(
        vararg vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            val vertexPathsAsString =
                vertexPath
                    .asSequence()
                    .joinToString(separator = ", ", prefix = "{ ", postfix = " }")
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """one or more vertices expected in the 
                  |metamodel.path_based_graph is/are missing: 
                  |[ vertex_paths: ${vertexPathsAsString} ]
                  """.flatten()
            )
        }
    }

    private fun <
        V : SchematicVertex> assessOrFindPathAndDataSourceKeyForWhichToCreateRetrievalFunctionSpec(
        sourceAttributeVertex: SourceAttributeVertex,
        context: MaterializationGraphVertexContext<V>,
    ): Pair<SchematicPath, DataSource.Key<*>> {
        return sourceAttributeVertex.path
            .some()
            .zip(
                sourceAttributeVertex.compositeAttribute
                    .getSourceAttributeByDataSource()
                    .keys
                    .firstOrNone()
            )
            .recurse { (p, dsKey) ->
                when (val pp = p.getParentPath().orNull()) {
                    /*
                     * Use the current pair if no parent path: a domain path (level == 1)
                     */
                    null -> (p to dsKey).right().some()
                    else -> {
                        /*
                         * Find the datasource.keys on the parent and find its parent if the dsKey is the same
                         * else use the current path-to-dskey pair since the parent is on a different datasource
                         * and would thus require its own retreival function spec
                         */
                        context.session.metamodelGraph.pathBasedGraph
                            .getVertex(pp)
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .flatMap { sct: SourceContainerTypeVertex ->
                                sct.compositeContainerType
                                    .getSourceContainerTypeByDataSource()
                                    .keys
                                    .firstOrNone()
                                    .flatMap { sctDsKey ->
                                        when (dsKey) {
                                            sctDsKey -> (pp to sctDsKey).some()
                                            else -> none()
                                        }
                                    }
                            }
                            .fold(
                                { (p to dsKey).right().some() },
                                { parentWithSameDsKey -> parentWithSameDsKey.left().some() }
                            )
                    }
                }
            }
            .successIfDefined()
            .orElseThrow()
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexWithSameNameInDifferentDomain(
        context: MaterializationGraphVertexContext<V>
    ): Option<SourceAttributeVertex> {
        return context.currentVertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                context.session.metamodelGraph.sourceAttributeVerticesByQualifiedName[name]
                    .toOption()
            }
            .flatMap { srcAttrs ->
                context.path.pathSegments.firstOrNone().flatMap { domainPathSegment ->
                    srcAttrs
                        .asSequence()
                        .firstOrNull { sav: SourceAttributeVertex ->
                            sav.path.pathSegments
                                .firstOrNone { firstPathSegment ->
                                    firstPathSegment != domainPathSegment
                                }
                                .isDefined()
                        }
                        .toOption()
                }
            }
    }

    private fun <V : ParameterAttributeVertex> findSourceAttributeVertexWithSameNameInSameDomain(
        context: MaterializationGraphVertexContext<V>
    ): Option<SourceAttributeVertex> {
        return context.currentVertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                context.session.metamodelGraph.sourceAttributeVerticesByQualifiedName[name]
                    .toOption()
            }
            .flatMap { srcAttrs ->
                context.path.pathSegments.firstOrNone().flatMap { domainPathSegment ->
                    srcAttrs
                        .asSequence()
                        .firstOrNull { sav: SourceAttributeVertex ->
                            sav.path.pathSegments
                                .firstOrNone { firstPathSegment ->
                                    firstPathSegment == domainPathSegment
                                }
                                .isDefined()
                        }
                        .toOption()
                }
            }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexByAliasReferenceInSameDomain(
        context: MaterializationGraphVertexContext<V>
    ): Option<SourceAttributeVertex> {
        return context.currentVertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                context.session.metamodelGraph.attributeAliasRegistry
                    .getSourceVertexPathWithSimilarNameOrAlias(name)
            }
            .flatMap { srcAttrPath ->
                srcAttrPath
                    .getParentPath()
                    .flatMap { pp ->
                        context.session.metamodelGraph.pathBasedGraph
                            .getVertex(pp)
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .map { sct: SourceContainerTypeVertex ->
                                sct.compositeContainerType.conventionalName.qualifiedForm
                            }
                            .zip(
                                context.session.metamodelGraph.pathBasedGraph
                                    .getVertex(srcAttrPath)
                                    .filterIsInstance<SourceAttributeVertex>()
                                    .map { sav: SourceAttributeVertex ->
                                        sav.compositeAttribute.conventionalName.qualifiedForm
                                    }
                            )
                            .flatMap { parentTypeSrcAttrName ->
                                context.session.metamodelGraph
                                    .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair[
                                        parentTypeSrcAttrName]
                                    .toOption()
                            }
                    }
                    .flatMap { srcAttrs ->
                        srcAttrs.firstOrNone { sav: SourceAttributeVertex ->
                            context.path.pathSegments
                                .firstOrNone()
                                .filter { domainPathSegment ->
                                    sav.path.pathSegments
                                        .firstOrNone { ps -> ps == domainPathSegment }
                                        .isDefined()
                                }
                                .toOption()
                                .isDefined()
                        }
                    }
            }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexByAliasReferenceInDifferentDomain(
        context: MaterializationGraphVertexContext<V>
    ): Option<SourceAttributeVertex> {
        return context.currentVertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                context.session.metamodelGraph.attributeAliasRegistry
                    .getSourceVertexPathWithSimilarNameOrAlias(name)
            }
            .flatMap { srcAttrPath ->
                srcAttrPath
                    .getParentPath()
                    .flatMap { pp ->
                        context.session.metamodelGraph.pathBasedGraph
                            .getVertex(pp)
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .map { sct: SourceContainerTypeVertex ->
                                sct.compositeContainerType.conventionalName.qualifiedForm
                            }
                            .zip(
                                context.session.metamodelGraph.pathBasedGraph
                                    .getVertex(srcAttrPath)
                                    .filterIsInstance<SourceAttributeVertex>()
                                    .map { sav: SourceAttributeVertex ->
                                        sav.compositeAttribute.conventionalName.qualifiedForm
                                    }
                            )
                            .flatMap { parentTypeSrcAttrName ->
                                context.session.metamodelGraph
                                    .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair[
                                        parentTypeSrcAttrName]
                                    .toOption()
                            }
                    }
                    .flatMap { srcAttrs ->
                        srcAttrs.firstOrNone { sav: SourceAttributeVertex ->
                            context.path.pathSegments
                                .firstOrNone()
                                .filter { domainPathSegment ->
                                    sav.path.pathSegments
                                        .firstOrNone { ps -> ps != domainPathSegment }
                                        .isDefined()
                                }
                                .toOption()
                                .isDefined()
                        }
                    }
            }
    }

    private fun argumentValueNotResolvedIntoJsonExceptionSupplier(
        vertexPath: SchematicPath,
        argument: Argument
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """graphql_value [ type: ${argument.value::class.qualifiedName} ] 
                   |for argument [ name: ${argument.name} ] 
                   |could not be resolved in JSON for 
                   |[ vertex_path: ${vertexPath} ]""".flatten()
            )
        }
    }

    override fun onParameterLeafVertex(
        context: ParameterLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_parameter_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            !context.argument.value.toOption().filterIsInstance<NullValue>().isDefined() -> {
                // add materialized value as an edge from this parameter to its source_vertex and
                // add this parameter_path to the ancestor function spec so that it can be used in
                // the request made to the source
                val ancestorRetrievalFunctionSpecEdge: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdge.updateSpec {
                                    addParameterVertex(context.currentVertex)
                                },
                                RequestParameterEdge::id
                            )
                            .putEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(context.parentPath.orNull()!!, context.path)
                                    .materializedValue(
                                        GraphQLValueToJsonNodeConverter.invoke(
                                                context.argument.value
                                            )
                                            .successIfDefined(
                                                argumentValueNotResolvedIntoJsonExceptionSupplier(
                                                    context.path,
                                                    context.argument
                                                )
                                            )
                                            .orElseThrow()
                                    )
                                    .build(),
                                RequestParameterEdge::id
                            )
                    )
                }
            }
            // case 2: caller has not provided an input value for this argument so it needs to be
            // retrieved through some other source--if possible
            else -> {
                val sourceAttributeVertexWithSameNameOrAlias =
                    findSourceAttributeVertexWithSameNameInSameDomain(context)
                        .or(findSourceAttributeVertexByAliasReferenceInSameDomain(context))
                        .or(findSourceAttributeVertexWithSameNameInDifferentDomain(context))
                        .or(findSourceAttributeVertexByAliasReferenceInDifferentDomain(context))
                when {
                    sourceAttributeVertexWithSameNameOrAlias.isDefined() -> {
                        getParameterAttributeVertexValueThroughSourceAttributeVertex(
                            sourceAttributeVertexWithSameNameOrAlias.orNull()!!,
                            context
                        )
                    }
                    else -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """parameter_junction_vertex could not be mapped to 
                                |source_attribute_vertex such that its materialized 
                                |value could be obtained: 
                                |[ vertex_path: ${context.path} 
                                |]""".flatten()
                        )
                    }
                }
            }
        }
    }
}
