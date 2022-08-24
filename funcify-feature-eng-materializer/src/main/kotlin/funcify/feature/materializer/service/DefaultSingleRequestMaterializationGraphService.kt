package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.DeferredExtensions.toDeferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.streams.asSequence
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestMaterializationGraphService(
    private val materializationGraphVertexContextFactory: MaterializationGraphVertexContextFactory,
    private val materializationGraphVertexConnector: MaterializationGraphVertexConnector
                                                              ) : SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationGraphService>()

        private data class FieldOrArgumentGraphContext(
            val parentPath: SchematicPath,
            val fieldOrArgument: Either<Field, Argument>
        )

        private sealed interface ResolvedVertexContext

        private data class ResolvedSourceVertexContext(
            val vertexPath: SchematicPath,
            val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>,
            val field: Field
        ) : ResolvedVertexContext

        private data class ResolvedParameterVertexContext(
            val vertexPath: SchematicPath,
            val parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>,
            val argument: Argument
        ) : ResolvedVertexContext
    }

    override fun createRequestMaterializationGraphForSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession> {
        logger.debug(
            "create_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        logger.debug(
            "field.selection_set: [ session.field.selection_set: ${session.field.selectionSet} ]"
        )
        logger.debug(
            "execution_step_info: [ {} ]",
            session.dataFetchingEnvironment.executionStepInfo.simplePrint()
        )
        logger.debug(
            "metamodel.parameter_attribute_vertices_by_source_attribute_vertex_paths: {}",
            session.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                .asSequence()
                .joinToString(
                    separator = ",\n",
                    prefix = "{ ",
                    postfix = " }",
                    transform = { (savPath, paramVerts) ->
                        "${savPath}: %s".format(
                            paramVerts
                                .asSequence()
                                .map { pav -> pav.path }
                                .joinToString(separator = ", ", prefix = "{ ", postfix = " }")
                        )
                    }
                )
        )
        // TODO: Add caching based on operation_definition input and parameterization of
        // materialized_values
        return traverseFieldInSessionCreatingMaterializationGraph(session).toDeferred().map { ctx ->
            session.update { requestMaterializationGraph(ctx.graph) }
        }
    }

    private fun createTopologicalSortString(context: MaterializationGraphVertexContext<*>): String {
        val requestParameterEdgeTypeComparator: Comparator<KClass<out RequestParameterEdge>> =
            kotlin.Comparator { cls1, cls2 ->
                when {
                    cls1 == cls2 -> 0
                    RequestParameterEdge.MaterializedValueRequestParameterEdge::class.isSubclassOf(
                        cls1
                    ) -> -1
                    RequestParameterEdge.MaterializedValueRequestParameterEdge::class.isSubclassOf(
                        cls2
                    ) -> 1
                    RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge::class
                        .isSubclassOf(cls1) -> -1
                    RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge::class
                        .isSubclassOf(cls2) -> 1
                    RequestParameterEdge.DependentValueRequestParameterEdge::class.isSubclassOf(
                        cls1
                    ) -> -1
                    RequestParameterEdge.DependentValueRequestParameterEdge::class.isSubclassOf(
                        cls2
                    ) -> 1
                    else -> {
                        0
                    }
                }
            }

        return context.graph
            .depthFirstSearchOnPath(SchematicPath.getRootPath())
            .asSequence()
            .map { (v1, p1, e, p2, v2) -> "%s --> %s --> %s".format(p1, e::class.simpleName, p2) }
            .joinToString(",\n--> ", "{ ", " }")
    }

    private fun traverseFieldInSessionCreatingMaterializationGraph(
        session: SingleRequestFieldMaterializationSession
    ): Try<MaterializationGraphVertexContext<*>> {
        return session.metamodelGraph.pathBasedGraph
            .getVertex(SchematicPath.getRootPath())
            .filterIsInstance<SourceRootVertex>()
            .successIfDefined(sourceVertexNotFoundExceptionSupplier(SchematicPath.getRootPath()))
            .map { sourceRootVertex: SourceRootVertex ->
                session.dataFetchingEnvironment.operationDefinition
                    .toOption()
                    .mapNotNull { opDef: OperationDefinition -> opDef.selectionSet }
                    .mapNotNull { ss: SelectionSet -> ss.selections }
                    .map { sList: List<Selection<*>> -> sList.asSequence() }
                    .fold(::emptySequence, ::identity)
                    .filterIsInstance<Field>()
                    .map { f: Field ->
                        FieldOrArgumentGraphContext(SchematicPath.getRootPath(), f.left())
                    }
                    .recurse { fieldOrArgCtx ->
                        when (fieldOrArgCtx.fieldOrArgument) {
                            is Either.Left -> {
                                resolveSourceVertexForField(
                                    fieldOrArgCtx.fieldOrArgument.value,
                                    fieldOrArgCtx,
                                    session
                                )
                            }
                            is Either.Right -> {
                                resolveParameterVertexForArgument(
                                    fieldOrArgCtx.fieldOrArgument.value,
                                    fieldOrArgCtx,
                                    session
                                )
                            }
                        }
                    }
                    .fold(
                        materializationGraphVertexConnector.connectSourceRootVertex(
                            materializationGraphVertexContextFactory.createSourceRootVertexContext(
                                sourceRootVertex,
                                session.metamodelGraph,
                                session.materializationSchema
                            )
                        )
                    ) {
                        materializationGraphVertexCtx: MaterializationGraphVertexContext<*>,
                        resolvedFieldOrArgContext ->
                        logger.debug(
                            "current_graph_state: {}",
                            createGraphStr(materializationGraphVertexCtx.graph)
                        )
                        when (resolvedFieldOrArgContext) {
                            is ResolvedSourceVertexContext -> {
                                materializationGraphVertexCtx.update {
                                    nextSourceVertex(
                                        resolvedFieldOrArgContext.sourceJunctionOrLeafVertex,
                                        resolvedFieldOrArgContext.field
                                    )
                                }
                            }
                            is ResolvedParameterVertexContext -> {
                                materializationGraphVertexCtx.update {
                                    nextParameterVertex(
                                        resolvedFieldOrArgContext.parameterJunctionOrLeafVertex,
                                        resolvedFieldOrArgContext.argument
                                    )
                                }
                            }
                        }.let { context ->
                            materializationGraphVertexConnector.connectSchematicVertex(context)
                        }
                    }
            }
    }

    private fun createGraphStr(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    ): String {
        val edgeToString: (RequestParameterEdge) -> String = { e ->
            StandardNamingConventions.SNAKE_CASE.deriveName(e::class.simpleName!!).qualifiedForm +
                "(" +
                (when (e) {
                    is RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge -> {
                        "datasource.key.name: " +
                            e.dataSource.key.name +
                            ", source_vertices.keys: " +
                            e.sourceVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "{ ", " }") +
                            ", parameter_vertices.keys: " +
                            e.parameterVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "{ ", " }")
                    }
                    is RequestParameterEdge.DependentValueRequestParameterEdge -> {
                        ""
                    }
                    is RequestParameterEdge.MaterializedValueRequestParameterEdge -> {
                        ""
                    }
                    else -> ""
                }) +
                ")"
        }
        return "%s\n%s".format(
            graph.verticesByPath.keys
                .asSequence()
                .joinToString(separator = ",\n", prefix = "vertices: { ", postfix = " }"),
            graph
                .edgesAsStream()
                .asSequence()
                .joinToString(
                    separator = ",\n",
                    prefix = "edges: { ",
                    postfix = " }",
                    transform = { e ->
                        "%s --> %s --> %s".format(e.id.first, edgeToString.invoke(e), e.id.second)
                    }
                )
        )
    }

    private fun resolveSourceVertexForField(
        field: Field,
        fieldOrArgCtx: FieldOrArgumentGraphContext,
        session: SingleRequestFieldMaterializationSession,
    ): Sequence<Either<FieldOrArgumentGraphContext, ResolvedSourceVertexContext>> {
        logger.debug("resolve_source_vertex_for_field: [ field.name: ${field.name} ]")
        val vertexPath: SchematicPath =
            SchematicPath.of {
                pathSegments(
                    fieldOrArgCtx.parentPath.pathSegments.asSequence().plus(field.name).toList()
                )
            }
        val sourceJunctionOrLeafVertex =
            when (
                    val sourceVertex: SchematicVertex =
                        session.metamodelGraph.pathBasedGraph
                            .getVertex(vertexPath)
                            .successIfDefined(sourceVertexNotFoundExceptionSupplier(vertexPath))
                            .orElseThrow()
                ) {
                    is SourceJunctionVertex -> {
                        sourceVertex.left().some()
                    }
                    is SourceLeafVertex -> {
                        sourceVertex.right().some()
                    }
                    else -> {
                        none()
                    }
                }
                .successIfDefined(sourceJunctionOrLeafVertexUnresolvedExceptionSupplier(vertexPath))
                .orElseThrow()
        return sequenceOf(
                ResolvedSourceVertexContext(vertexPath, sourceJunctionOrLeafVertex, field).right()
            )
            .plus(
                field.arguments.asSequence().map { argument: Argument ->
                    FieldOrArgumentGraphContext(vertexPath, argument.right()).left()
                }
            )
            .plus(
                field
                    .toOption()
                    .mapNotNull { f: Field -> f.selectionSet }
                    .mapNotNull { ss: SelectionSet -> ss.selections }
                    .map { sList: List<Selection<*>> -> sList.asSequence() }
                    .fold(::emptySequence, ::identity)
                    .filterIsInstance<Field>()
                    .map { f: Field -> FieldOrArgumentGraphContext(vertexPath, f.left()).left() }
            )
    }

    private fun resolveParameterVertexForArgument(
        argument: Argument,
        fieldOrArgCtx: FieldOrArgumentGraphContext,
        session: SingleRequestFieldMaterializationSession,
    ): Sequence<Either<FieldOrArgumentGraphContext, ResolvedParameterVertexContext>> {
        logger.debug("resolve_parameter_vertex_for_argument: [ argument.name: ${argument.name} ]")
        val vertexPath: SchematicPath =
            SchematicPath.of {
                pathSegments(fieldOrArgCtx.parentPath.pathSegments).argument(argument.name)
            }
        val parameterJunctionOrLeafVertex =
            when (
                    val parameterVertex: SchematicVertex =
                        session.metamodelGraph.pathBasedGraph
                            .getVertex(vertexPath)
                            .successIfDefined(parameterVertexNotFoundExceptionSupplier(vertexPath))
                            .orElseThrow()
                ) {
                    is ParameterJunctionVertex -> {
                        parameterVertex.left().some()
                    }
                    is ParameterLeafVertex -> {
                        parameterVertex.right().some()
                    }
                    else -> {
                        none()
                    }
                }
                .successIfDefined(
                    parameterJunctionOrLeafVertexUnresolvedExceptionSupplier(vertexPath)
                )
                .orElseThrow()
        // TODO: Add support for argument.value.object_fields if argument.value is of type
        // ObjectValue
        return sequenceOf(
            ResolvedParameterVertexContext(vertexPath, parameterJunctionOrLeafVertex, argument)
                .right()
        )
    }

    private fun sourceVertexNotFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                """source_junction_vertex or source_leaf_vertex 
                   |expected but not found in 
                   |metamodel_graph.path_based_graph for path 
                   |[ vertex_path: $vertexPath ]""".flatten()
            )
        }
    }

    private fun parameterVertexNotFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                """parameter_junction_vertex or parameter_leaf_vertex 
                   |expected in 
                   |metamodel_graph.path_based_graph for path 
                   |but not found 
                   |[ vertex_path: $vertexPath ]""".flatten()
            )
        }
    }

    private fun sourceJunctionOrLeafVertexUnresolvedExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """source_vertex for [ vertex_path: ${vertexPath} ] 
                    |did not resolve to either a 
                    |source_junction_vertex or 
                    |source_leaf_vertex as expected""".flatten()
            )
        }
    }

    private fun parameterJunctionOrLeafVertexUnresolvedExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """parameter_vertex for [ vertex_path: ${vertexPath} ] 
                    |did not resolve to either a 
                    |parameter_junction_vertex or 
                    |parameter_leaf_vertex as expected""".flatten()
            )
        }
    }
}