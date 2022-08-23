package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.json.GraphQLValueToJsonNodeConverter
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdgeFactory
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.ParameterLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceJunctionMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceLeafMaterializationGraphVertexContext
import funcify.feature.materializer.service.MaterializationGraphVertexContext.SourceRootMaterializationGraphVertexContext
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
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
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-17
 */
internal class DefaultMaterializationGraphVertexConnector(
    private val jsonMapper: JsonMapper,
    private val requestParameterEdgeFactory: RequestParameterEdgeFactory,
) : MaterializationGraphVertexConnector {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphVertexConnector>()
    }

    override fun connectSourceRootVertex(
        context: SourceRootMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_source_root_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return if (context.graph.getVertex(context.path).isDefined()) {
            context
        } else {
            context.update {
                graph(context.graph.putVertex(SchematicPath.getRootPath(), context.currentVertex))
            }
        }
    }

    override fun connectSourceJunctionVertex(
        context: SourceJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_source_junction_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when { // TODO: can combine cases where top-level node and no
            // ancestor_retrieval_function_spec:
            // both requiring assessment of data_source.key and creation of the
            // retrieval_function_spec edge

            // case 1: child of root => a domain node but already has an edge from root to this node
            // defined wherein a spec is defined for this connection
            context.path.isChildTo(SchematicPath.getRootPath()) &&
                context.graph
                    .getEdgesFromPathToPath(SchematicPath.getRootPath(), context.path)
                    .firstOrNone()
                    .filterIsInstance<RetrievalFunctionSpecRequestParameterEdge>()
                    .filter { specEdge -> specEdge.sourceVerticesByPath.containsKey(context.path) }
                    .isDefined() -> {
                context
            } // case 2: child of root => a domain node but without a request_parameter_edge defined
            context.path.isChildTo(SchematicPath.getRootPath()) -> {
                val topLevelDataSourceForVertex: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(
                        context.metamodelGraph,
                        context.currentVertex
                    )
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
            // Case 3: source_junction_vertex does not support same data_source as ancestor
            // spec_edge
            findAncestorRetrievalFunctionSpecRequestParameterEdge(context)
                .getSuccess()
                .filter { specEdge ->
                    !context.currentVertex.compositeAttribute
                        .getSourceAttributeByDataSource()
                        .containsKey(specEdge.dataSource.key)
                }
                .isDefined() -> {
                val topLevelDataSourceForVertex: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(
                        context.metamodelGraph,
                        context.currentVertex
                    )
                val edge =
                    requestParameterEdgeFactory
                        .builder()
                        .fromPathToPath(context.path.getParentPath().orNull()!!, context.path)
                        .retrievalFunctionSpecForDataSource(topLevelDataSourceForVertex) {
                            addSourceVertex(context.currentVertex)
                        }
                        .build()
                context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                    .getOrNone(context.path)
                    .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                    .fold(::emptySequence, ::identity)
                    .fold(
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(edge, RequestParameterEdge::id)
                            )
                        } as MaterializationGraphVertexContext<*>
                    ) { ctx, parameterAttributeVertex ->
                        connectSchematicVertex(
                            ctx.update {
                                nextParameterVertex(
                                    (when (parameterAttributeVertex) {
                                        is ParameterJunctionVertex ->
                                            parameterAttributeVertex.left()
                                        is ParameterLeafVertex -> parameterAttributeVertex.right()
                                        else -> null
                                    }!!)
                                )
                            }
                        )
                    }
            }
            else -> {
                val ancestorSpecEdge: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                    .getOrNone(context.path)
                    .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                    .fold(::emptySequence, ::identity)
                    .fold(
                        context.update {
                            graph(
                                context.graph
                                    .putVertex(context.currentVertex, SchematicVertex::path)
                                    .putEdge(
                                        ancestorSpecEdge.updateSpec {
                                            addSourceVertex(context.currentVertex)
                                        },
                                        RequestParameterEdge::id
                                    )
                                    .putEdge(
                                        requestParameterEdgeFactory
                                            .builder()
                                            .fromPathToPath(
                                                ancestorSpecEdge.id.second,
                                                context.path
                                            )
                                            .extractionFromAncestorFunction { resultMap ->
                                                resultMap.getOrNone(context.path)
                                            }
                                            .build(),
                                        RequestParameterEdge::id
                                    )
                            )
                        } as MaterializationGraphVertexContext<*>
                    ) { ctx, parameterAttributeVertex ->
                        connectSchematicVertex(
                            ctx.update {
                                nextParameterVertex(
                                    (when (parameterAttributeVertex) {
                                        is ParameterJunctionVertex ->
                                            parameterAttributeVertex.left()
                                        is ParameterLeafVertex -> parameterAttributeVertex.right()
                                        else -> null
                                    }!!)
                                )
                            }
                        )
                    }
            }
        }
    }

    private fun selectDataSourceForSourceAttributeVertex(
        metamodelGraph: MetamodelGraph,
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
            .some()
            .recurse { p ->
                when (
                    val edgeBetweenParentAndCurrent: RequestParameterEdge? =
                        p.getParentPath()
                            .flatMap { pp -> graph.getEdgesFromPathToPath(pp, p).firstOrNone() }
                            .orNull()
                ) {
                    is RetrievalFunctionSpecRequestParameterEdge -> {
                        edgeBetweenParentAndCurrent.right().some()
                    }
                    else -> {
                        p.getParentPath().map { pp -> pp.left() }
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

    override fun connectSourceLeafVertex(
        context: SourceLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_source_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: child of root => a domain node but already has an edge from root to this node
            // defined wherein a spec is defined for this connection
            context.path.isChildTo(SchematicPath.getRootPath()) &&
                context.graph
                    .getEdgesFromPathToPath(SchematicPath.getRootPath(), context.path)
                    .firstOrNone()
                    .filterIsInstance<RetrievalFunctionSpecRequestParameterEdge>()
                    .filter { specEdge -> specEdge.sourceVerticesByPath.containsKey(context.path) }
                    .isDefined() -> {
                context
            } // case 2: child of root => a domain node but without a request_parameter_edge defined
            context.path.isChildTo(SchematicPath.getRootPath()) -> {
                val topLevelDataSourceForVertex: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(
                        context.metamodelGraph,
                        context.currentVertex
                    )
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
            } // case 3: not a child of root so there should be an ancestor with a
            // retrieval_function_spec edge defined
            else -> {
                val ancestorRetrievalFunctionSpec: RetrievalFunctionSpecRequestParameterEdge =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
                val ancestorRetrievalSpecDataSourceKey: DataSource.Key<*> =
                    ancestorRetrievalFunctionSpec.dataSource.key
                when { // case 1: source_leaf_vertex has a representation in the same
                    // data_source
                    // as the one in the spec being constructed
                    context.currentVertex.compositeAttribute
                        .getSourceAttributeForDataSourceKey(ancestorRetrievalSpecDataSourceKey)
                        .toOption()
                        .isDefined() -> { // add vertex to this spec
                        context.metamodelGraph
                            .parameterAttributeVerticesBySourceAttributeVertexPaths
                            .getOrNone(context.path)
                            .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                            .fold(::emptySequence, ::identity)
                            .fold(
                                context.update {
                                    graph(
                                        context.graph
                                            .putVertex(context.path, context.currentVertex)
                                            .putEdge(
                                                ancestorRetrievalFunctionSpec.updateSpec {
                                                    addSourceVertex(context.currentVertex)
                                                },
                                                RequestParameterEdge::id
                                            ) // add edge connecting parent to child indicating
                                            // extraction
                                            // should be done on the ancestor result node
                                            .putEdge(
                                                requestParameterEdgeFactory
                                                    .builder()
                                                    .fromPathToPath(
                                                        ancestorRetrievalFunctionSpec.id.second,
                                                        context.path
                                                    )
                                                    .extractionFromAncestorFunction {
                                                        parentResultMap ->
                                                        parentResultMap[context.path].toOption()
                                                    }
                                                    .build(),
                                                RequestParameterEdge::id
                                            )
                                    )
                                } as MaterializationGraphVertexContext<*>
                            ) { ctx, parameterAttributeVertex ->
                                connectSchematicVertex(
                                    ctx.update {
                                        nextParameterVertex(
                                            (when (parameterAttributeVertex) {
                                                is ParameterJunctionVertex ->
                                                    parameterAttributeVertex.left()
                                                is ParameterLeafVertex ->
                                                    parameterAttributeVertex.right()
                                                else -> null
                                            }!!)
                                        )
                                    }
                                )
                            }
                    }
                    // case 3: source_leaf_vertex does not have a representation in the same
                    // ancestor_retrieval_function_spec so it cannot be fetched from the same
                    // data_source
                    // and requires its own retrieval spec
                    // it does not have a field defined, so its arguments won't be processed later
                    // and thus must be processed now
                    else -> {
                        val dataSourceForVertex: DataSource<*> =
                            selectDataSourceForSourceAttributeVertex(
                                context.metamodelGraph,
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
                        context.metamodelGraph
                            .parameterAttributeVerticesBySourceAttributeVertexPaths
                            .getOrNone(context.path)
                            .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                            .fold(::emptySequence, ::identity)
                            .fold(
                                context.update {
                                    graph(
                                        context.graph
                                            .putVertex(context.path, context.currentVertex)
                                            .putEdge(edge, RequestParameterEdge::id)
                                    )
                                } as MaterializationGraphVertexContext<*>
                            ) { ctx, parameterAttributeVertex ->
                                connectSchematicVertex(
                                    ctx.update {
                                        nextParameterVertex(
                                            (when (parameterAttributeVertex) {
                                                is ParameterJunctionVertex ->
                                                    parameterAttributeVertex.left()
                                                is ParameterLeafVertex ->
                                                    parameterAttributeVertex.right()
                                                else -> null
                                            }!!)
                                        )
                                    }
                                )
                            }
                    }
                }
            }
        }
    }

    override fun connectParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_parameter_junction_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            context.argument.isDefined() &&
                !context.argument
                    .map(Argument::getValue)
                    .filterIsInstance<NullValue>()
                    .isDefined() -> {
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
                                                context.argument.orNull()!!.value
                                            )
                                            .successIfDefined(
                                                argumentValueNotResolvedIntoJsonExceptionSupplier(
                                                    context.path,
                                                    context.argument.orNull()!!
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
                        .orElse { findSourceAttributeVertexByAliasReferenceInSameDomain(context) }
                        .orElse { findSourceAttributeVertexWithSameNameInDifferentDomain(context) }
                        .orElse {
                            findSourceAttributeVertexByAliasReferenceInDifferentDomain(context)
                        }
                when {
                    // case 2.1: source is another source_attribute_vertex by same name or alias
                    sourceAttributeVertexWithSameNameOrAlias.isDefined() -> {
                        getParameterAttributeVertexValueThroughSourceAttributeVertex(
                            sourceAttributeVertexWithSameNameOrAlias.orNull()!!,
                            context
                        )
                    }
                    // case 2.2: source is a default argument value from the schema
                    correspondingFieldDefinitionHasNonNullDefaultArgumentValuePresent(context) -> {
                        val materializedDefaultJsonValue: JsonNode =
                            getCorrespondingFieldDefinitionNonNullDefaultArgumentValue(context)
                                .successIfDefined(
                                    defaultArgumentValueNotResolvedIntoJsonExceptionSupplier(
                                        context.path
                                    )
                                )
                                .orElseThrow()
                        // add materialized value as an edge from this parameter to its
                        // source_vertex and
                        // add this parameter_path to the ancestor function spec so that it can be
                        // used in
                        // the request made to the source
                        val ancestorRetrievalFunctionSpecEdge:
                            RetrievalFunctionSpecRequestParameterEdge =
                            findAncestorRetrievalFunctionSpecRequestParameterEdge(context)
                                .orElseThrow()
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
                                            .fromPathToPath(
                                                context.parentPath.orNull()!!,
                                                context.path
                                            )
                                            .materializedValue(materializedDefaultJsonValue)
                                            .build(),
                                        RequestParameterEdge::id
                                    )
                            )
                        }
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
        V : ParameterAttributeVertex
    > correspondingFieldDefinitionHasNonNullDefaultArgumentValuePresent(
        context: MaterializationGraphVertexContext<V>
    ): Boolean {
        return getCorrespondingFieldDefinitionArgumentInSchema(context)
            .filter { graphQLArgument: GraphQLArgument ->
                graphQLArgument.hasSetDefaultValue() &&
                    graphQLArgument.argumentDefaultValue.value != null
            }
            .isDefined()
    }

    private fun <V : ParameterAttributeVertex> getCorrespondingFieldDefinitionArgumentInSchema(
        context: MaterializationGraphVertexContext<V>
    ): Option<GraphQLArgument> {
        return context.path
            .toOption()
            .map { p -> SchematicPath.of { pathSegments(p.pathSegments) } }
            .flatMap { sourceVertexPath ->
                sourceVertexPath
                    .getParentPath()
                    .flatMap { parentSourceVertexPath ->
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(parentSourceVertexPath)
                            .filterIsInstance<SourceContainerTypeVertex>()
                    }
                    .zip(
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(sourceVertexPath)
                            .filterIsInstance<SourceAttributeVertex>()
                    )
            }
            .mapNotNull { (sct, sa) ->
                FieldCoordinates.coordinates(
                    sct.compositeContainerType.conventionalName.qualifiedForm,
                    sa.compositeAttribute.conventionalName.qualifiedForm
                )
            }
            .mapNotNull { fieldCoords -> context.graphQLSchema.getFieldDefinition(fieldCoords) }
            .flatMap { fieldDef ->
                fieldDef
                    .getArgument(
                        context.currentVertex.compositeParameterAttribute.conventionalName
                            .qualifiedForm
                    )
                    .toOption()
            }
    }

    private fun <
        V : ParameterAttributeVertex> getCorrespondingFieldDefinitionNonNullDefaultArgumentValue(
        context: MaterializationGraphVertexContext<V>
    ): Option<JsonNode> {
        return getCorrespondingFieldDefinitionArgumentInSchema(context).flatMap {
            graphQLArgument: GraphQLArgument ->
            val defaultValueHolder = graphQLArgument.argumentDefaultValue
            when {
                defaultValueHolder.isLiteral && defaultValueHolder.value is Value<*> -> {
                    GraphQLValueToJsonNodeConverter.invoke(defaultValueHolder.value as Value<*>)
                }
                defaultValueHolder.isInternal -> {
                    jsonMapper.fromKotlinObject(defaultValueHolder.value).toJsonNode().getSuccess()
                }
                defaultValueHolder.isExternal -> {
                    jsonMapper.fromKotlinObject(defaultValueHolder.value).toJsonNode().getSuccess()
                }
                else -> {
                    none()
                }
            }
        }
    }

    private fun <
        V : ParameterAttributeVertex> getParameterAttributeVertexValueThroughSourceAttributeVertex(
        sourceAttributeVertex: SourceAttributeVertex,
        context: MaterializationGraphVertexContext<V>,
    ): MaterializationGraphVertexContext<*> {
        val parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex> =
            when (val v = context.currentVertex) {
                is ParameterJunctionVertex -> v.left()
                is ParameterLeafVertex -> v.right()
                else -> null
            }!!
        return when {
            // Case 1: source_attribute_vertex already has entry in spec_edge
            // connect this parameter to that edge with an extraction_function
            findAncestorRetrievalFunctionSpecRequestParameterEdge(
                    sourceAttributeVertex.path,
                    context.graph
                )
                .getSuccess()
                .filter { specEdge ->
                    specEdge.sourceVerticesByPath.containsKey(sourceAttributeVertex.path)
                }
                .isDefined() -> {
                val ancestorRetrievalFunctionSpecEdgeForSourceAttributeVertex =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(
                            sourceAttributeVertex.path,
                            context.graph
                        )
                        .orElseThrow()
                val ancestorRetrievalFunctionSpecEdgeForParameterAttributeVertex =
                    findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
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
                                        jsonValuesMap.getOrNone(sourceAttributeVertex.path)
                                    }
                                    .build(),
                                RequestParameterEdge::id
                            )
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdgeForParameterAttributeVertex
                                    .updateSpec {
                                        parameterJunctionOrLeafVertex.fold(
                                            { pjv -> addParameterVertex(pjv) },
                                            { plv -> addParameterVertex(plv) }
                                        )
                                    },
                                RequestParameterEdge::id
                            )
                    )
                }
            }
            // case 2: source_attribute_vertex does not have entry in spec_edge
            // add source_attribute_vertex through call to its connect_X method
            else -> {
                val sourceOrJunctionVertex: Either<SourceJunctionVertex, SourceLeafVertex> =
                    when (sourceAttributeVertex) {
                        is SourceJunctionVertex -> sourceAttributeVertex.left()
                        is SourceLeafVertex -> sourceAttributeVertex.right()
                        else -> null
                    }!!
                connectSchematicVertex(context.update { nextSourceVertex(sourceOrJunctionVertex) })
                    .let { updatedContext ->
                        val ancestorRetrievalFunctionSpecEdgeForSourceAttributeVertex =
                            findAncestorRetrievalFunctionSpecRequestParameterEdge(
                                    sourceAttributeVertex.path,
                                    updatedContext.graph
                                )
                                .orElseThrow()
                        val ancestorRetrievalFunctionSpecEdgeForParameterAttributeVertex =
                            findAncestorRetrievalFunctionSpecRequestParameterEdge(
                                    context.path,
                                    updatedContext.graph
                                )
                                .orElseThrow()
                        updatedContext.update {
                            graph(
                                updatedContext.graph
                                    .putVertex(context.path, context.currentVertex)
                                    .putEdge(
                                        requestParameterEdgeFactory
                                            .builder()
                                            .fromPathToPath(
                                                ancestorRetrievalFunctionSpecEdgeForSourceAttributeVertex
                                                    .id
                                                    .second,
                                                context.path
                                            )
                                            .extractionFromAncestorFunction { resultJsonMap ->
                                                resultJsonMap.getOrNone(sourceAttributeVertex.path)
                                            }
                                            .build(),
                                        RequestParameterEdge::id
                                    )
                                    .putEdge(
                                        ancestorRetrievalFunctionSpecEdgeForParameterAttributeVertex
                                            .updateSpec {
                                                parameterJunctionOrLeafVertex.fold(
                                                    { pjv -> addParameterVertex(pjv) },
                                                    { plv -> addParameterVertex(plv) }
                                                )
                                            },
                                        RequestParameterEdge::id
                                    )
                            )
                        }
                    }
            }
        }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexWithSameNameInDifferentDomain(
        context: MaterializationGraphVertexContext<V>
    ): Option<SourceAttributeVertex> {
        return context.currentVertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                context.metamodelGraph.sourceAttributeVerticesByQualifiedName[name].toOption()
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
                context.metamodelGraph.sourceAttributeVerticesByQualifiedName[name].toOption()
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
                context.metamodelGraph.attributeAliasRegistry
                    .getSourceVertexPathWithSimilarNameOrAlias(name)
            }
            .flatMap { srcAttrPath ->
                srcAttrPath
                    .getParentPath()
                    .flatMap { pp ->
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(pp)
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .map { sct: SourceContainerTypeVertex ->
                                sct.compositeContainerType.conventionalName.qualifiedForm
                            }
                            .zip(
                                context.metamodelGraph.pathBasedGraph
                                    .getVertex(srcAttrPath)
                                    .filterIsInstance<SourceAttributeVertex>()
                                    .map { sav: SourceAttributeVertex ->
                                        sav.compositeAttribute.conventionalName.qualifiedForm
                                    }
                            )
                            .flatMap { parentTypeSrcAttrName ->
                                context.metamodelGraph
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
                context.metamodelGraph.attributeAliasRegistry
                    .getSourceVertexPathWithSimilarNameOrAlias(name)
            }
            .flatMap { srcAttrPath ->
                srcAttrPath
                    .getParentPath()
                    .flatMap { pp ->
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(pp)
                            .filterIsInstance<SourceContainerTypeVertex>()
                            .map { sct: SourceContainerTypeVertex ->
                                sct.compositeContainerType.conventionalName.qualifiedForm
                            }
                            .zip(
                                context.metamodelGraph.pathBasedGraph
                                    .getVertex(srcAttrPath)
                                    .filterIsInstance<SourceAttributeVertex>()
                                    .map { sav: SourceAttributeVertex ->
                                        sav.compositeAttribute.conventionalName.qualifiedForm
                                    }
                            )
                            .flatMap { parentTypeSrcAttrName ->
                                context.metamodelGraph
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

    private fun defaultArgumentValueNotResolvedIntoJsonExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """graphql_default_argument_value for [ vertex_path: ${vertexPath} ] 
                   |could not be extracted or
                   |converted into JSON""".flatten()
            )
        }
    }

    override fun connectParameterLeafVertex(
        context: ParameterLeafMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_parameter_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            context.argument.isDefined() &&
                !context.argument
                    .map(Argument::getValue)
                    .filterIsInstance<NullValue>()
                    .isDefined() -> {
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
                                                context.argument.orNull()!!.value
                                            )
                                            .successIfDefined(
                                                argumentValueNotResolvedIntoJsonExceptionSupplier(
                                                    context.path,
                                                    context.argument.orNull()!!
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
                        .orElse { findSourceAttributeVertexByAliasReferenceInSameDomain(context) }
                        .orElse { findSourceAttributeVertexWithSameNameInDifferentDomain(context) }
                        .orElse {
                            findSourceAttributeVertexByAliasReferenceInDifferentDomain(context)
                        }
                when {
                    // case 2.1: source is another source_attribute_vertex by same name or alias
                    sourceAttributeVertexWithSameNameOrAlias.isDefined() -> {
                        getParameterAttributeVertexValueThroughSourceAttributeVertex(
                            sourceAttributeVertexWithSameNameOrAlias.orNull()!!,
                            context
                        )
                    }
                    // case 2.2: source is a default argument value from the schema
                    correspondingFieldDefinitionHasNonNullDefaultArgumentValuePresent(context) -> {
                        val materializedDefaultJsonValue: JsonNode =
                            getCorrespondingFieldDefinitionNonNullDefaultArgumentValue(context)
                                .successIfDefined(
                                    defaultArgumentValueNotResolvedIntoJsonExceptionSupplier(
                                        context.path
                                    )
                                )
                                .orElseThrow()
                        // add materialized value as an edge from this parameter to its
                        // source_vertex and
                        // add this parameter_path to the ancestor function spec so that it can be
                        // used in
                        // the request made to the source
                        val ancestorRetrievalFunctionSpecEdge:
                            RetrievalFunctionSpecRequestParameterEdge =
                            findAncestorRetrievalFunctionSpecRequestParameterEdge(context)
                                .orElseThrow()
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
                                            .fromPathToPath(
                                                context.parentPath.orNull()!!,
                                                context.path
                                            )
                                            .materializedValue(materializedDefaultJsonValue)
                                            .build(),
                                        RequestParameterEdge::id
                                    )
                            )
                        }
                    }
                    else -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """parameter_leaf_vertex could not be mapped to 
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
