package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.context.graph.MaterializationGraphContext
import funcify.feature.materializer.context.graph.MaterializationGraphContextFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.phase.DefaultRequestParameterMaterializationGraphPhase
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestMaterializationGraphService(
    private val materializationGraphContextFactory: MaterializationGraphContextFactory,
    private val materializationGraphConnector: MaterializationGraphConnector
) : SingleRequestMaterializationGraphService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationGraphService>()

        private data class FieldOrArgumentGraphContext(
            val parentPath: GQLOperationPath,
            val fieldOrArgument: Either<Field, Argument>
        )

        private sealed interface ResolvedVertexContext

        private data class ResolvedSourceVertexContext(
            val vertexPath: GQLOperationPath,
            val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex>,
            val field: Field
        ) : ResolvedVertexContext

        private data class ResolvedParameterVertexContext(
            val vertexPath: GQLOperationPath,
            val parameterJunctionOrLeafVertex: Either<ParameterJunctionVertex, ParameterLeafVertex>,
            val argument: Argument
        ) : ResolvedVertexContext
    }

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Try<GraphQLSingleRequestSession> {
        logger.debug(
            "create_request_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        // TODO: Add caching based on operation_definition input and parameterization of
        // materialized_values
        if (session.requestParameterMaterializationGraphPhase.isDefined()) {
            return Try.success(session)
        }
        if (!session.document.isDefined() || !session.operationDefinition.isDefined()) {
            return Try.failure(
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """both document and operation_definition for graphql 
                        |must be defined before creating request_materialization_graph""".flatten()
                )
            )
        }

        return traverseOperationDefinitionInSessionCreatingMaterializationGraph(session).map {
            context: MaterializationGraphContext ->
            session.update {
                requestParameterMaterializationGraphPhase(
                    DefaultRequestParameterMaterializationGraphPhase(
                        requestGraph = context.requestParameterGraph,
                        materializedParameterValuesByPath =
                            context.materializedParameterValuesByPath.toPersistentMap(),
                        parameterIndexPathsBySourceIndexPath =
                            context.parameterIndexPathsBySourceIndexPath,
                        retrievalFunctionSpecByTopSourceIndexPath =
                            context.retrievalFunctionSpecByTopSourceIndexPath
                    )
                )
            }
        }
    }

    private fun traverseOperationDefinitionInSessionCreatingMaterializationGraph(
        session: GraphQLSingleRequestSession
    ): Try<MaterializationGraphContext> {
        logger.debug(
            "traverse_operation_definition_in_session_creating_materialization_graph: [ session.session_id: {} ]",
            session.sessionId
        )
        return session.metamodelGraph.pathBasedGraph
            .getVertex(GQLOperationPath.getRootPath())
            .filterIsInstance<SourceRootVertex>()
            .successIfDefined(sourceVertexNotFoundExceptionSupplier(GQLOperationPath.getRootPath()))
            .map { sourceRootVertex: SourceRootVertex ->
                session.operationDefinition
                    .mapNotNull { opDef: OperationDefinition -> opDef.selectionSet }
                    .mapNotNull { ss: SelectionSet -> ss.selections }
                    .map { sList: List<Selection<*>> -> sList.asSequence() }
                    .fold(::emptySequence, ::identity)
                    .filterIsInstance<Field>()
                    .map { f: Field ->
                        FieldOrArgumentGraphContext(GQLOperationPath.getRootPath(), f.left())
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
                        materializationGraphConnector.connectSourceRootVertex(
                            sourceRootVertex,
                            materializationGraphContextFactory
                                .builder()
                                .operationDefinition(session.operationDefinition.orNull()!!)
                                .materializationMetamodel(session.materializationMetamodel)
                                .queryVariables(session.processedQueryVariables.toPersistentMap())
                                .build()
                        )
                    ) {
                            materializationGraphContext: MaterializationGraphContext,
                            resolvedFieldOrArgContext: ResolvedVertexContext ->
                        when (resolvedFieldOrArgContext) {
                            is ResolvedSourceVertexContext -> {
                                materializationGraphConnector.connectSourceJunctionOrLeafVertex(
                                    resolvedFieldOrArgContext.field.toOption(),
                                    resolvedFieldOrArgContext.sourceJunctionOrLeafVertex.fold(
                                        ::identity,
                                        ::identity
                                    ),
                                    materializationGraphContext
                                )
                            }
                            is ResolvedParameterVertexContext -> {
                                materializationGraphConnector.connectParameterJunctionOrLeafVertex(
                                    resolvedFieldOrArgContext.argument.toOption(),
                                    resolvedFieldOrArgContext.parameterJunctionOrLeafVertex.fold(
                                        ::identity,
                                        ::identity
                                    ),
                                    materializationGraphContext
                                )
                            }
                        }
                    }
            }
    }

    private fun resolveSourceVertexForField(
        field: Field,
        fieldOrArgCtx: FieldOrArgumentGraphContext,
        session: GraphQLSingleRequestSession,
    ): Sequence<Either<FieldOrArgumentGraphContext, ResolvedSourceVertexContext>> {
        logger.debug("resolve_source_vertex_for_field: [ field.name: ${field.name} ]")
        val vertexPath: GQLOperationPath =
            GQLOperationPath.of {
                fields(
                    fieldOrArgCtx.parentPath.selection.asSequence().plus(field.name).toList()
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
        session: GraphQLSingleRequestSession,
    ): Sequence<Either<FieldOrArgumentGraphContext, ResolvedParameterVertexContext>> {
        logger.debug("resolve_parameter_vertex_for_argument: [ argument.name: ${argument.name} ]")
        val vertexPath: GQLOperationPath =
            GQLOperationPath.of {
                fields(fieldOrArgCtx.parentPath.selection).argument(argument.name)
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
        vertexPath: GQLOperationPath
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
        vertexPath: GQLOperationPath
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
        vertexPath: GQLOperationPath
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
        vertexPath: GQLOperationPath
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
