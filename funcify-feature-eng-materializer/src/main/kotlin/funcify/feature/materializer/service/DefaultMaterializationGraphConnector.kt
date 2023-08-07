package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.tools.json.JsonMapper
import funcify.feature.materializer.context.graph.MaterializationGraphContext
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.materializer.schema.edge.RequestParameterEdge
import funcify.feature.materializer.schema.edge.RequestParameterEdgeFactory
import funcify.feature.materializer.schema.path.ListIndexedSchematicPathGraphQLSchemaBasedCalculator
import funcify.feature.materializer.schema.path.SchematicPathFieldCoordinatesMatcher
import funcify.feature.materializer.schema.path.SourceAttributeDataSourceAncestorPathFinder
import funcify.feature.materializer.schema.vertex.ParameterToSourceAttributeVertexMatcher
import funcify.feature.materializer.service.DefaultMaterializationGraphConnector.Companion.ContextUpdater
import funcify.feature.materializer.spec.DefaultRetrievalFunctionSpec
import funcify.feature.materializer.spec.RetrievalFunctionSpec
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.language.Argument
import graphql.language.Field
import graphql.language.NullValue
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.GraphQLArgument
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.streams.asSequence
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-10-09
 */
internal class DefaultMaterializationGraphConnector(
    private val jsonMapper: JsonMapper,
    private val requestParameterEdgeFactory: RequestParameterEdgeFactory,
) : MaterializationGraphConnector {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphConnector>()
        private fun interface ContextUpdater :
                (MaterializationGraphContext.Builder) -> MaterializationGraphContext.Builder
    }
    override fun connectSourceRootVertex(
        vertex: SourceRootVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        logger.info("connect_source_root_vertex: [ vertex.path: {} ]", vertex.path)
        // Current implementation does not require anything be done at the root level
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
        if (sourceJunctionOrLeafVertexAlreadyConnected(vertex, context)) {
            return context
        }
        val currentOrAncestorPathWithSameDataSource: GQLOperationPath =
            SourceAttributeDataSourceAncestorPathFinder(
                context.materializationMetamodel,
                vertex.path
            )
        return when (vertex.path) {
            currentOrAncestorPathWithSameDataSource -> {
                addSourceAttributeVertexUnderOwnSpecInContext(vertex, context)
            }
            else -> {
                addSourceAttributeVertexUnderAncestorSpecInContext(
                    vertex,
                    currentOrAncestorPathWithSameDataSource,
                    context
                )
            }
        }
    }

    private fun <V : SourceAttributeVertex> sourceJunctionOrLeafVertexAlreadyConnected(
        vertex: V,
        context: MaterializationGraphContext
    ): Boolean {
        return context.requestParameterGraph.getEdgesFrom(vertex.path).count() > 0L
    }

    private fun <V : SourceAttributeVertex> addSourceAttributeVertexUnderOwnSpecInContext(
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        return selectDataSourceForSourceAttributeVertex(vertex, context)
            .zip(deriveSourceJunctionLeafVertexEither(vertex))
            .map { (selectedDatasource, sourceJunctionOrLeafVertex) ->
                val newOrUpdatedSpec: RetrievalFunctionSpec =
                    if (vertex.path in context.retrievalFunctionSpecByTopSourceIndexPath) {
                        context.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(vertex.path)
                            .filter { spec -> spec.dataSource.key == selectedDatasource.key }
                            .successIfDefined(
                                specAlreadyDefinedForDifferentDataSourceExceptionSupplier(
                                    vertex.path,
                                    selectedDatasource.key,
                                    context.retrievalFunctionSpecByTopSourceIndexPath
                                )
                            )
                            .map { spec ->
                                spec.updateSpec { addSourceVertex(sourceJunctionOrLeafVertex) }
                            }
                            .orNull()!!
                    } else {
                        DefaultRetrievalFunctionSpec(
                            selectedDatasource,
                            persistentMapOf(vertex.path to sourceJunctionOrLeafVertex)
                        )
                    }
                val contextUpdaterFunc: ContextUpdater = ContextUpdater { bldr ->
                    bldr
                        .addVertexToRequestParameterGraph(vertex)
                        .addRetrievalFunctionSpecForTopSourceIndexPath(
                            vertex.path,
                            newOrUpdatedSpec
                        )
                }
                context.update(contextUpdaterFunc)
            }
            .map(addAnyParameterVerticesForSourceVertexToContext(vertex))
            .map(addAnyLastUpdatedAttributeVerticesForSourceVertexToContext(vertex))
            .map(addAnyEntityIdentifierAttributeVerticesForSourceVertexToContext(vertex))
            .orElseThrow()
    }

    private fun selectDataSourceForSourceAttributeVertex(
        sourceAttributeVertex: SourceAttributeVertex,
        context: MaterializationGraphContext
    ): Try<DataElementSource<*>> {
        return sourceAttributeVertex.compositeAttribute
            .getSourceAttributeByDataSource()
            .keys
            .singleOrNone()
            .successIfDefined(
                moreThanOneDataSourceFoundExceptionSupplier(sourceAttributeVertex.path)
            )
            .flatMap { dsKey ->
                context.metamodelGraph.dataSourcesByKey
                    .getOrNone(dsKey)
                    .successIfDefined(
                        dataSourceNotFoundExceptionSupplier(
                            dsKey,
                            context.metamodelGraph.dataSourcesByKey.keys.toPersistentSet()
                        )
                    )
            }
    }

    private fun moreThanOneDataSourceFoundExceptionSupplier(
        vertexPath: GQLOperationPath
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
        dataSourceKey: DataElementSource.Key<*>,
        availableDataSourceKeys: ImmutableSet<DataElementSource.Key<*>>
    ): () -> MaterializerException {
        return { ->
            val dataSourceKeysAvailable =
                availableDataSourceKeys
                    .asSequence()
                    .joinToString(
                        separator = ", ",
                        prefix = "{ ",
                        postfix = " }",
                        transform = { d -> "${d.name}: ${d.sourceType}" }
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

    private fun <V : SourceAttributeVertex> deriveSourceJunctionLeafVertexEither(
        vertex: V
    ): Try<Either<SourceJunctionVertex, SourceLeafVertex>> {
        return when (vertex) {
            is SourceJunctionVertex -> vertex.left()
            is SourceLeafVertex -> vertex.right()
            else -> null
        }.successIfNonNull(sourceVertexNeitherJunctionNorLeafExceptionSupplier(vertex))
    }

    private fun sourceVertexNeitherJunctionNorLeafExceptionSupplier(
        vertex: SchematicVertex
    ): () -> MaterializerException {
        return { ->
            val vertexType: String =
                vertex::class
                    .supertypes
                    .asIterable()
                    .firstOrNone()
                    .map(KType::classifier)
                    .filterIsInstance<KClass<*>>()
                    .mapNotNull(KClass<*>::qualifiedName)
                    .orElse {
                        vertex::class
                            .toOption()
                            .mapNotNull(KClass<out SchematicVertex>::qualifiedName)
                    }
                    .getOrElse { "<NA>" }
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """vertex is not a source_junction_vertex 
                    |or source_leaf_vertex and thus cannot 
                    |be handled by this method: [ actual type: %s ]"""
                    .flatten()
                    .format(vertexType)
            )
        }
    }

    private fun specAlreadyDefinedForDifferentDataSourceExceptionSupplier(
        path: GQLOperationPath,
        selectedDatasourceKey: DataElementSource.Key<*>,
        retrievalFunctionSpecByTopSourceIndexPath:
            PersistentMap<GQLOperationPath, RetrievalFunctionSpec>
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """a retrieval_function_spec has already been defined for 
                    |[ path: %s ] 
                    |but for a different datasource 
                    |[ expected_datasource.key: %s, 
                    | actual_datasource.key: %s ]"""
                    .flatten()
                    .format(
                        path,
                        selectedDatasourceKey,
                        retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(path)
                            .map(RetrievalFunctionSpec::dataSource)
                            .map(DataElementSource<*>::key)
                            .orNull()
                    )
            )
        }
    }

    private fun <V : SourceAttributeVertex> addSourceAttributeVertexUnderAncestorSpecInContext(
        vertex: V,
        ancestorPath: GQLOperationPath,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        return context.metamodelGraph.pathBasedGraph
            .getVertex(ancestorPath)
            .filterIsInstance<SourceAttributeVertex>()
            .successIfDefined(vertexNotFoundAtPathExceptionSupplier(ancestorPath))
            .flatMap { ancestorVertex ->
                context.retrievalFunctionSpecByTopSourceIndexPath
                    .getOrNone(ancestorPath)
                    .and(context.toOption())
                    .orElse {
                        connectSourceJunctionOrLeafVertex(none(), ancestorVertex, context)
                            .toOption()
                            .filter { updatedContext ->
                                ancestorVertex.path in
                                    updatedContext.retrievalFunctionSpecByTopSourceIndexPath
                            }
                    }
                    .successIfDefined(retrievalFunctionSpecMissingExceptionSupplier(ancestorPath))
            }
            .zip(deriveSourceJunctionLeafVertexEither(vertex))
            .flatMap { (updatedContext, sjvOrSlv) ->
                updatedContext.retrievalFunctionSpecByTopSourceIndexPath
                    .toOption()
                    .flatMap { specsByPath -> specsByPath.getOrNone(ancestorPath) }
                    .map { spec -> spec.updateSpec { addSourceVertex(sjvOrSlv) } }
                    .map { updatedSpec -> updatedContext to updatedSpec }
                    .successIfDefined(retrievalFunctionSpecMissingExceptionSupplier(ancestorPath))
            }
            .map { (updatedContext, updatedSpec) ->
                val listIndexedPath =
                    getVertexPathWithListIndexingIfDescendentOfListNode(vertex, context)
                val contextUpdaterFunc: ContextUpdater = ContextUpdater { bldr ->
                    bldr
                        .addVertexToRequestParameterGraph(vertex)
                        .addRetrievalFunctionSpecForTopSourceIndexPath(ancestorPath, updatedSpec)
                        .addEdgeToRequestParameterGraph(
                            requestParameterEdgeFactory
                                .builder()
                                .fromPathToPath(vertex.path, ancestorPath)
                                .dependentExtractionFunction { resultMap ->
                                    resultMap.getOrNone(listIndexedPath)
                                }
                                .build()
                        )
                }
                updatedContext.update(contextUpdaterFunc)
            }
            .map(addAnyParameterVerticesForSourceVertexToContext(vertex))
            .map(addAnyLastUpdatedAttributeVerticesForSourceVertexToContext(vertex))
            .map(addAnyEntityIdentifierAttributeVerticesForSourceVertexToContext(vertex))
            .orElseThrow()
    }

    private fun <
        V : SourceAttributeVertex> addAnyEntityIdentifierAttributeVerticesForSourceVertexToContext(
        vertex: V
    ): (MaterializationGraphContext) -> MaterializationGraphContext {
        return { context ->
            context.metamodelGraph.entityRegistry
                .findNearestEntityIdentifierPathRelatives(vertex.path)
                .asSequence()
                .filter { entityIdPath ->
                    vertex.path != entityIdPath &&
                        entityIdPath !in context.requestParameterGraph.verticesByPath
                }
                .map { entityIdPath ->
                    context.metamodelGraph.pathBasedGraph.getVertex(entityIdPath)
                }
                .flatMapOptions()
                .filterIsInstance<SourceAttributeVertex>()
                .fold(context) { ctx, entityIdVertex ->
                    connectSourceJunctionOrLeafVertex(none(), entityIdVertex, ctx)
                }
        }
    }

    private fun <
        V : SourceAttributeVertex> addAnyLastUpdatedAttributeVerticesForSourceVertexToContext(
        vertex: V,
    ): (MaterializationGraphContext) -> MaterializationGraphContext {
        return { context ->
            context.metamodelGraph.lastUpdatedTemporalAttributePathRegistry
                .findNearestLastUpdatedTemporalAttributePathRelative(vertex.path)
                .filter { lastUpdatedAttributePath ->
                    vertex.path != lastUpdatedAttributePath &&
                        lastUpdatedAttributePath !in context.requestParameterGraph.verticesByPath
                }
                .flatMap { lastUpdatedAttributePath ->
                    context.metamodelGraph.pathBasedGraph
                        .getVertex(lastUpdatedAttributePath)
                        .filterIsInstance<SourceAttributeVertex>()
                        .map { sav: SourceAttributeVertex ->
                            connectSourceJunctionOrLeafVertex(none(), sav, context)
                        }
                }
                .getOrElse { context }
        }
    }

    private fun <V : SourceAttributeVertex> addAnyParameterVerticesForSourceVertexToContext(
        vertex: V
    ): (MaterializationGraphContext) -> MaterializationGraphContext {
        return { context ->
            context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                .getOrNone(vertex.path)
                .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                .fold(::emptySequence, ::identity)
                .filter { paramAttrVertex ->
                    paramAttrVertex.path !in context.requestParameterGraph.verticesByPath
                }
                .fold(context) { ctx, paramAttrVertex ->
                    connectParameterJunctionOrLeafVertex(none(), paramAttrVertex, ctx)
                }
        }
    }

    private fun vertexNotFoundAtPathExceptionSupplier(
        path: GQLOperationPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """vertex expected but not found at [ path: %s ] 
                    |within metamodel_graph"""
                    .flatten()
                    .format(path)
            )
        }
    }

    private fun <V : SourceAttributeVertex> getVertexPathWithListIndexingIfDescendentOfListNode(
        vertex: V,
        context: MaterializationGraphContext
    ): GQLOperationPath {
        return ListIndexedSchematicPathGraphQLSchemaBasedCalculator(
                vertex.path,
                context.graphQLSchema
            )
            .successIfDefined(listIndexedPathCalculationExceptionSupplier(vertex))
            .orElseThrow()
    }

    private fun <V : SourceAttributeVertex> listIndexedPathCalculationExceptionSupplier(
        vertex: V
    ): () -> MaterializerException {
        return {
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """unable to calculate list-indexed version of path 
                    |[ vertex.path: ${vertex.path} ]
                    |""".flatten()
            )
        }
    }

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
        return when {
            argumentIsDefinedAndHasNonNullValue(argument) -> {
                addParameterAttributeVertexWithGivenArgumentMaterializedValueEdgeToContext(
                    argument.orNull()!!,
                    vertex,
                    context
                )
            }
            fieldDefinitionWithArgumentHasNonNullDefaultArgumentValue(vertex, context) -> {
                addParameterAttributeVertexWithDefaultArgumentMaterializedValueEdgeToContext(
                    vertex,
                    context
                )
            }
            ParameterToSourceAttributeVertexMatcher(context.materializationMetamodel, vertex.path)
                .isDefined() -> {
                addParameterAttributeVertexDependentOnSourceAttributeVertexEdgeToContext(
                    vertex,
                    context
                )
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """parameter_junction_or_leaf_vertex could not be mapped to 
                    |source_attribute_vertex such that its materialized 
                    |value could be obtained: 
                    |[ vertex_path: ${vertex.path} 
                    |]""".flatten()
                )
            }
        }
    }

    private fun argumentIsDefinedAndHasNonNullValue(argument: Option<Argument>): Boolean {
        return argument.isDefined() &&
            !argument.map(Argument::getValue).filterIsInstance<NullValue>().isDefined()
    }

    private fun <
        V : ParameterAttributeVertex
    > addParameterAttributeVertexWithGivenArgumentMaterializedValueEdgeToContext(
        argument: Argument,
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        return ensureCorrespondingOrAncestorSourceIndexAlreadyHasRetrievalFunctionSpec(
                vertex,
                context
            )
            .zip(deriveParameterJunctionLeafVertexEither(vertex))
            .flatMap { (updatedContext, parameterJunctionOrLeafVertex) ->
                val sourceIndexPath: GQLOperationPath = vertex.path.transform { clearArguments() }
                val topLevelSourceIndexPath =
                    SourceAttributeDataSourceAncestorPathFinder(
                        updatedContext.materializationMetamodel,
                        sourceIndexPath
                    )
                GraphQLValueToJsonNodeConverter(argument.value)
                    .orElse {
                        extractValueForVariableIfArgumentIsVariableReference(argument, context)
                    }
                    .successIfDefined(
                        argumentValueNotResolvedIntoJsonExceptionSupplier(vertex.path, argument)
                    )
                    .zip(
                        updatedContext.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(topLevelSourceIndexPath)
                            .successIfDefined(
                                retrievalFunctionSpecMissingExceptionSupplier(
                                    topLevelSourceIndexPath
                                )
                            )
                    )
                    .map { (materializedValueJson, spec) ->
                        val contextUpdater: ContextUpdater = ContextUpdater { bldr ->
                            bldr
                                .addVertexToRequestParameterGraph(vertex)
                                .addEdgeToRequestParameterGraph(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(sourceIndexPath, vertex.path)
                                        .materializedValue(materializedValueJson)
                                        .build()
                                )
                                .addParameterIndexPathForSourceIndexPath(
                                    sourceIndexPath,
                                    vertex.path
                                )
                                .addMaterializedParameterValueForPath(
                                    vertex.path,
                                    materializedValueJson
                                )
                                .addRetrievalFunctionSpecForTopSourceIndexPath(
                                    topLevelSourceIndexPath,
                                    spec.updateSpec {
                                        addParameterVertex(parameterJunctionOrLeafVertex)
                                    }
                                )
                            if (
                                updatedContext.requestParameterGraph
                                    .getEdgesFrom(vertex.path)
                                    .count() > 0
                            ) {
                                // This vertex should not "depend" on any other if a
                                // materialized_value has been given for it
                                updatedContext.requestParameterGraph
                                    .getEdgesFrom(vertex.path)
                                    .map(RequestParameterEdge::id)
                                    .asSequence()
                                    .fold(bldr) { b, edgeId ->
                                        b.removeEdgesFromRequestParameterGraph(edgeId)
                                    }
                            } else {
                                bldr
                            }
                        }
                        updatedContext.update(contextUpdater)
                    }
            }
            .orElseThrow()
    }

    private fun <V : ParameterAttributeVertex> deriveParameterJunctionLeafVertexEither(
        vertex: V
    ): Try<Either<ParameterJunctionVertex, ParameterLeafVertex>> {
        return when (vertex) {
            is ParameterJunctionVertex -> vertex.left()
            is ParameterLeafVertex -> vertex.right()
            else -> null
        }.successIfNonNull(parameterVertexNeitherJunctionNorLeafExceptionSupplier(vertex))
    }

    private fun parameterVertexNeitherJunctionNorLeafExceptionSupplier(
        vertex: SchematicVertex
    ): () -> MaterializerException {
        return { ->
            val vertexType: String =
                vertex::class
                    .supertypes
                    .asIterable()
                    .firstOrNone()
                    .map(KType::classifier)
                    .filterIsInstance<KClass<*>>()
                    .mapNotNull(KClass<*>::qualifiedName)
                    .orElse {
                        vertex::class
                            .toOption()
                            .mapNotNull(KClass<out SchematicVertex>::qualifiedName)
                    }
                    .getOrElse { "<NA>" }
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """vertex is not a parameter_junction_vertex 
                    |or parameter_leaf_vertex and thus cannot 
                    |be handled by this method: [ actual type: %s ]"""
                    .flatten()
                    .format(vertexType)
            )
        }
    }

    private fun retrievalFunctionSpecMissingExceptionSupplier(
        sourceIndexPath: GQLOperationPath
    ): () -> MaterializerException {
        return {
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """retrieval_function_spec not available 
                    |for source_index_path [ path: %s ]"""
                    .flatten()
                    .format(sourceIndexPath)
            )
        }
    }

    private fun argumentValueNotResolvedIntoJsonExceptionSupplier(
        vertexPath: GQLOperationPath,
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

    private fun <
        V : ParameterAttributeVertex
    > ensureCorrespondingOrAncestorSourceIndexAlreadyHasRetrievalFunctionSpec(
        vertex: V,
        context: MaterializationGraphContext
    ): Try<MaterializationGraphContext> {
        val sourceIndexPath: GQLOperationPath = vertex.path.transform { clearArguments() }
        val topLevelSourceIndexPath: GQLOperationPath =
            SourceAttributeDataSourceAncestorPathFinder(
                context.materializationMetamodel,
                sourceIndexPath
            )
        return context.retrievalFunctionSpecByTopSourceIndexPath
            .getOrNone(topLevelSourceIndexPath)
            .and(context.requestParameterGraph.getVertex(topLevelSourceIndexPath))
            .and(context.requestParameterGraph.getVertex(sourceIndexPath))
            .and(context.toOption())
            .orElse {
                context.metamodelGraph.pathBasedGraph
                    .getVertex(topLevelSourceIndexPath)
                    .filterIsInstance<SourceAttributeVertex>()
                    .flatMap { sav ->
                        selectDataSourceForSourceAttributeVertex(sav, context).getSuccess()
                    }
                    .zip(
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(topLevelSourceIndexPath)
                            .filterIsInstance<SourceAttributeVertex>()
                            .mapNotNull { sav ->
                                when (sav) {
                                    is SourceJunctionVertex -> sav.left()
                                    is SourceLeafVertex -> sav.right()
                                    else -> null
                                }
                            },
                        context.metamodelGraph.pathBasedGraph
                            .getVertex(sourceIndexPath)
                            .filterIsInstance<SourceAttributeVertex>()
                            .mapNotNull { sav ->
                                when (sav) {
                                    is SourceJunctionVertex -> sav.left()
                                    is SourceLeafVertex -> sav.right()
                                    else -> null
                                }
                            },
                        ::Triple
                    )
                    .map { (ds, topSjvOrSlv, sjvOrSlv) ->
                        val contextUpdater: ContextUpdater = ContextUpdater { bldr ->
                            bldr
                                .addRetrievalFunctionSpecForTopSourceIndexPath(
                                    topLevelSourceIndexPath,
                                    DefaultRetrievalFunctionSpec(ds).updateSpec {
                                        addSourceVertex(topSjvOrSlv).addSourceVertex(sjvOrSlv)
                                    }
                                )
                                .addVertexToRequestParameterGraph(
                                    topSjvOrSlv.fold(::identity, ::identity)
                                )
                                .addVertexToRequestParameterGraph(
                                    sjvOrSlv.fold(::identity, ::identity)
                                )
                            if (topLevelSourceIndexPath != sourceIndexPath) {
                                val listIndexedPath: GQLOperationPath =
                                    getVertexPathWithListIndexingIfDescendentOfListNode(
                                        sjvOrSlv.fold(::identity, ::identity),
                                        context
                                    )
                                bldr.addEdgeToRequestParameterGraph(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(sourceIndexPath, topLevelSourceIndexPath)
                                        .dependentExtractionFunction { resultMap ->
                                            resultMap.getOrNone(listIndexedPath)
                                        }
                                        .build()
                                )
                            } else {
                                bldr
                            }
                        }
                        context.update(contextUpdater)
                    }
            }
            .successIfDefined(
                retrievalFunctionSpecMissingExceptionSupplier(topLevelSourceIndexPath)
            )
    }

    private fun <
        V : ParameterAttributeVertex
    > addParameterAttributeVertexDependentOnSourceAttributeVertexEdgeToContext(
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        return ensureCorrespondingOrAncestorSourceIndexAlreadyHasRetrievalFunctionSpec(
                vertex,
                context
            )
            .zip(deriveParameterJunctionLeafVertexEither(vertex))
            .flatMap { (updatedContext, parameterJunctionOrLeafVertex) ->
                val sourceIndexPath: GQLOperationPath = vertex.path.transform { clearArguments() }
                val topLevelSourceIndexPath =
                    SourceAttributeDataSourceAncestorPathFinder(
                        updatedContext.materializationMetamodel,
                        sourceIndexPath
                    )
                ParameterToSourceAttributeVertexMatcher(
                        updatedContext.materializationMetamodel,
                        vertex.path
                    )
                    .successIfDefined(
                        matchingSourceAttributeVertexNotFoundExceptionSupplier(vertex)
                    )
                    .flatMap { matchedSrcAttrVertex: SourceAttributeVertex ->
                        matchedSrcAttrVertex
                            .toOption()
                            .filter { v ->
                                v.path in updatedContext.requestParameterGraph.verticesByPath
                            }
                            .map { v -> v to updatedContext }
                            .orElse {
                                connectSourceJunctionOrLeafVertex(
                                        none(),
                                        matchedSrcAttrVertex,
                                        updatedContext
                                    )
                                    .toOption()
                                    .filter { ctx ->
                                        matchedSrcAttrVertex.path in
                                            ctx.requestParameterGraph.verticesByPath
                                    }
                                    .map { ctx -> matchedSrcAttrVertex to ctx }
                            }
                            .successIfDefined(
                                correspondingSourceAttributeVertexNotAddedExceptionSupplier(
                                    matchedSrcAttrVertex.path
                                )
                            )
                    }
                    .flatMap { (matchedSrcAttrVertex, latestContext) ->
                        latestContext.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(topLevelSourceIndexPath)
                            .successIfDefined(
                                retrievalFunctionSpecMissingExceptionSupplier(
                                    topLevelSourceIndexPath
                                )
                            )
                            .map { spec -> Triple(matchedSrcAttrVertex, spec, latestContext) }
                    }
                    .map { (matchedSrcAttrVertex, spec, latestContext) ->
                        val listIndexedPath: GQLOperationPath =
                            getVertexPathWithListIndexingIfDescendentOfListNode(
                                matchedSrcAttrVertex,
                                latestContext
                            )
                        val contextUpdater: ContextUpdater = ContextUpdater { bldr ->
                            bldr
                                .addVertexToRequestParameterGraph(vertex)
                                .addEdgeToRequestParameterGraph(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(vertex.path, matchedSrcAttrVertex.path)
                                        .dependentExtractionFunction { resultMap ->
                                            resultMap.getOrNone(listIndexedPath)
                                        }
                                        .build()
                                )
                                .addParameterIndexPathForSourceIndexPath(
                                    sourceIndexPath,
                                    vertex.path
                                )
                                .addRetrievalFunctionSpecForTopSourceIndexPath(
                                    topLevelSourceIndexPath,
                                    spec.updateSpec {
                                        addParameterVertex(parameterJunctionOrLeafVertex)
                                    }
                                )
                        }
                        latestContext.update(contextUpdater)
                    }
            }
            .orElseThrow()
    }

    private fun matchingSourceAttributeVertexNotFoundExceptionSupplier(
        parameterAttributeVertex: ParameterAttributeVertex
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """no matching source_attribute_vertex for 
                    |parameter_attribute_vertex 
                    |[ path: %s ] was found"""
                    .flatten()
                    .format(parameterAttributeVertex.path)
            )
        }
    }

    private fun correspondingSourceAttributeVertexNotAddedExceptionSupplier(
        path: GQLOperationPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """the source_attribute_vertex corresponding to 
                    |parameter_attribute_vertex [ path: %s ] 
                    |was not successfully added to the context"""
                    .flatten()
                    .format(path)
            )
        }
    }

    private fun <
        V : ParameterAttributeVertex
    > addParameterAttributeVertexWithDefaultArgumentMaterializedValueEdgeToContext(
        vertex: V,
        context: MaterializationGraphContext,
    ): MaterializationGraphContext {
        return ensureCorrespondingOrAncestorSourceIndexAlreadyHasRetrievalFunctionSpec(
                vertex,
                context
            )
            .zip2(
                getCorrespondingFieldDefinitionNonNullDefaultArgumentValue(vertex, context)
                    .successIfDefined(
                        defaultArgumentValueNotResolvedIntoJsonExceptionSupplier(vertex.path)
                    ),
                deriveParameterJunctionLeafVertexEither(vertex)
            )
            .flatMap { (updatedContext, defaultJsonValue, parameterJunctionOrLeafVertex) ->
                val sourceIndexPath: GQLOperationPath = vertex.path.transform { clearArguments() }
                val topLevelSourceIndexPath =
                    SourceAttributeDataSourceAncestorPathFinder(
                        updatedContext.materializationMetamodel,
                        sourceIndexPath
                    )
                topLevelSourceIndexPath
                    .toOption()
                    .flatMap { ancestorPath ->
                        updatedContext.retrievalFunctionSpecByTopSourceIndexPath
                            .getOrNone(ancestorPath)
                            .map { spec -> ancestorPath to spec }
                    }
                    .successIfDefined(
                        retrievalFunctionSpecMissingExceptionSupplier(topLevelSourceIndexPath)
                    )
                    .map { specByAncestorPath ->
                        val contextUpdater: ContextUpdater = ContextUpdater { bldr ->
                            bldr
                                .addVertexToRequestParameterGraph(vertex)
                                .addEdgeToRequestParameterGraph(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(sourceIndexPath, vertex.path)
                                        .materializedValue(defaultJsonValue)
                                        .build()
                                )
                                .addMaterializedParameterValueForPath(vertex.path, defaultJsonValue)
                                .addParameterIndexPathForSourceIndexPath(
                                    sourceIndexPath,
                                    vertex.path
                                )
                                .addRetrievalFunctionSpecForTopSourceIndexPath(
                                    specByAncestorPath.first,
                                    specByAncestorPath.second.updateSpec {
                                        addParameterVertex(parameterJunctionOrLeafVertex)
                                    }
                                )
                        }
                        updatedContext.update(contextUpdater)
                    }
            }
            .orElseThrow()
    }

    private fun defaultArgumentValueNotResolvedIntoJsonExceptionSupplier(
        vertexPath: GQLOperationPath
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

    private fun <
        V : ParameterAttributeVertex> fieldDefinitionWithArgumentHasNonNullDefaultArgumentValue(
        vertex: V,
        context: MaterializationGraphContext
    ): Boolean {
        return getCorrespondingFieldDefinitionArgumentInSchema(vertex, context)
            .filter { graphQLArgument: GraphQLArgument ->
                graphQLArgument.hasSetDefaultValue() &&
                    when {
                        graphQLArgument.argumentDefaultValue.isLiteral ->
                            graphQLArgument.argumentDefaultValue.value !is NullValue
                        else -> graphQLArgument.argumentDefaultValue.value != null
                    }
            }
            .isDefined()
    }

    private fun <V : ParameterAttributeVertex> getCorrespondingFieldDefinitionArgumentInSchema(
        vertex: V,
        context: MaterializationGraphContext
    ): Option<GraphQLArgument> {
        return SchematicPathFieldCoordinatesMatcher(
                context.materializationMetamodel,
                vertex.path.transform { clearArguments() }
            )
            .mapNotNull { fieldCoords -> context.graphQLSchema.getFieldDefinition(fieldCoords) }
            .flatMap { fieldDef ->
                fieldDef
                    .getArgument(vertex.compositeParameterAttribute.conventionalName.qualifiedForm)
                    .toOption()
            }
    }

    private fun <
        V : ParameterAttributeVertex> getCorrespondingFieldDefinitionNonNullDefaultArgumentValue(
        vertex: V,
        context: MaterializationGraphContext
    ): Option<JsonNode> {
        return getCorrespondingFieldDefinitionArgumentInSchema(vertex, context).flatMap {
            graphQLArgument: GraphQLArgument ->
            val defaultValueHolder = graphQLArgument.argumentDefaultValue
            when {
                defaultValueHolder.isLiteral && defaultValueHolder.value is NullValue -> {
                    none()
                }
                defaultValueHolder.isLiteral && defaultValueHolder.value is Value<*> -> {
                    GraphQLValueToJsonNodeConverter.invoke(defaultValueHolder.value as Value<*>)
                }
                (defaultValueHolder.isExternal || defaultValueHolder.isInternal) &&
                    defaultValueHolder.value == null -> {
                    none()
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

    private fun extractValueForVariableIfArgumentIsVariableReference(
        argument: Argument,
        context: MaterializationGraphContext
    ): Option<JsonNode> {
        return argument
            .toOption()
            .map { a -> a.value }
            .filterIsInstance<VariableReference>()
            .map { vr -> vr.name }
            .flatMap { vName ->
                context.operationDefinition.variableDefinitions
                    .asSequence()
                    .filter { vd -> vd.name == vName }
                    .firstOrNull()
                    .toOption()
            }
            .flatMap { vd ->
                context.queryVariables
                    .getOrNone(vd.name)
                    .flatMap { anyValue ->
                        jsonMapper.fromKotlinObject(anyValue).toJsonNode().getSuccess()
                    }
                    .orElse {
                        vd.defaultValue.toOption().flatMap { defaultVariableValue ->
                            GraphQLValueToJsonNodeConverter.invoke(defaultVariableValue)
                        }
                    }
            }
    }
}
