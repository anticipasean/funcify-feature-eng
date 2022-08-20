package funcify.feature.materializer.service

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.singleOrNone
import arrow.core.some
import arrow.core.toOption
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
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.NullValue
import graphql.schema.SelectedField
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
            context.path.isChildTo(SchematicPath.getRootPath()) -> {
                val topLevelDataSourceKeyForVertex: DataSource.Key<*> =
                    context.currentVertex.compositeAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .singleOrNone()
                        .successIfDefined(moreThanOneDataSourceFoundExceptionSupplier(context.path))
                        .orElseThrow()
                val topLevelDataSourceForVertex: DataSource<*> =
                    context.session.metamodelGraph.dataSourcesByKey[topLevelDataSourceKeyForVertex]
                        .toOption()
                        .successIfDefined(
                            dataSourceNotFoundExceptionSupplier(
                                topLevelDataSourceKeyForVertex,
                                context.session.metamodelGraph.dataSourcesByKey.keys
                                    .toPersistentSet()
                            )
                        )
                        .orElseThrow()
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
                        val dataSourceKeyForVertex: DataSource.Key<*> =
                            context.currentVertex.compositeAttribute
                                .getSourceAttributeByDataSource()
                                .keys
                                .singleOrNone()
                                .successIfDefined(
                                    moreThanOneDataSourceFoundExceptionSupplier(context.path)
                                )
                                .orElseThrow()
                        val dataSourceForVertex: DataSource<*> =
                            context.session.metamodelGraph.dataSourcesByKey[dataSourceKeyForVertex]
                                .toOption()
                                .successIfDefined(
                                    dataSourceNotFoundExceptionSupplier(
                                        dataSourceKeyForVertex,
                                        context.session.metamodelGraph.dataSourcesByKey.keys
                                            .toPersistentSet()
                                    )
                                )
                                .orElseThrow()
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

    private fun findAncestorRetrievalFunctionSpecRequestParameterEdge(
        context: MaterializationGraphVertexContext<*>
    ): Try<RetrievalFunctionSpecRequestParameterEdge> {
        return context.parentPath
            .recurse { pp ->
                when (
                    val edgeBetweenGrandparentAndParent: RequestParameterEdge? =
                        pp.getParentPath()
                            .flatMap { ppp ->
                                context.graph.getEdgesFromPathToPath(ppp, pp).firstOrNone()
                            }
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
            .successIfDefined(ancestorRetrievalFunctionSpecNotFoundExceptionSupplier(context.path))
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
                val topLevelDataSourceKeyForVertex: DataSource.Key<*> =
                    context.currentVertex.compositeAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .singleOrNone()
                        .successIfDefined(moreThanOneDataSourceFoundExceptionSupplier(context.path))
                        .orElseThrow()
                val topLevelDataSourceForVertex: DataSource<*> =
                    context.session.metamodelGraph.dataSourcesByKey[topLevelDataSourceKeyForVertex]
                        .toOption()
                        .successIfDefined(
                            dataSourceNotFoundExceptionSupplier(
                                topLevelDataSourceKeyForVertex,
                                context.session.metamodelGraph.dataSourcesByKey.keys
                                    .toPersistentSet()
                            )
                        )
                        .orElseThrow()
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
                        val dataSourceKeyForVertex: DataSource.Key<*> =
                            context.currentVertex.compositeAttribute
                                .getSourceAttributeByDataSource()
                                .keys
                                .singleOrNone()
                                .successIfDefined(
                                    moreThanOneDataSourceFoundExceptionSupplier(context.path)
                                )
                                .orElseThrow()
                        val dataSourceForVertex: DataSource<*> =
                            context.session.metamodelGraph.dataSourcesByKey[dataSourceKeyForVertex]
                                .toOption()
                                .successIfDefined(
                                    dataSourceNotFoundExceptionSupplier(
                                        dataSourceKeyForVertex,
                                        context.session.metamodelGraph.dataSourcesByKey.keys
                                            .toPersistentSet()
                                    )
                                )
                                .orElseThrow()
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

    override fun onParameterJunctionVertex(
        context: ParameterJunctionMaterializationGraphVertexContext
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "on_parameter_junction_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        val ancestorRetrievalFunctionSpecEdge: RetrievalFunctionSpecRequestParameterEdge =
            findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            !context.argument.value.toOption().filterIsInstance<NullValue>().isDefined() -> {
                // add materialized value as an edge from this parameter to its source_vertex and
                // add this parameter_path to the ancestor function spec so that it can be used in
                // the request made to the source
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdge.id,
                                ancestorRetrievalFunctionSpecEdge.updateSpec {
                                    addParameterVertex(context.currentVertex)
                                }
                            )
                            .putEdge(
                                context.parentPath.orNull()!!,
                                context.path,
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
                                    .build()
                            )
                    )
                }
            }
            // case 2: caller has not provided an input value for this argument so it needs to be
            // retrieved through some other source--if possible
            else -> {
                val sourceAttributeVertexWithSameNameInSameDomain =
                    sourceAttributeVertexWithSameNameInSameDomain(context)
                val sourceAttributeVertexWithSameNameInDifferentDomain =
                    sourceAttributeVertexWithSameNameInDifferentDomain(context)
                val sourceAttributeVertexByAliasReferenceInSameDomain =
                    sourceAttributeVertexByAliasReferenceInSameDomain(context)
                val sourceAttributeVertexByAliasReferenceInDifferentDomain =
                    sourceAttributeVertexByAliasReferenceInDifferentDomain(context)
                when {
                    sourceAttributeVertexWithSameNameInSameDomain.isDefined() -> {}
                    sourceAttributeVertexWithSameNameInDifferentDomain.isDefined() -> {}
                    sourceAttributeVertexByAliasReferenceInSameDomain.isDefined() -> {}
                    sourceAttributeVertexByAliasReferenceInDifferentDomain.isDefined() -> {}
                    else -> {}
                }
                context
            }
        }
    }

    private fun sourceAttributeVertexWithSameNameInDifferentDomain(
        context: ParameterJunctionMaterializationGraphVertexContext
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

    private fun sourceAttributeVertexWithSameNameInSameDomain(
        context: ParameterJunctionMaterializationGraphVertexContext
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

    private fun sourceAttributeVertexByAliasReferenceInSameDomain(
        context: ParameterJunctionMaterializationGraphVertexContext
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

    private fun sourceAttributeVertexByAliasReferenceInDifferentDomain(
        context: ParameterJunctionMaterializationGraphVertexContext
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
        val ancestorRetrievalFunctionSpecEdge: RetrievalFunctionSpecRequestParameterEdge =
            findAncestorRetrievalFunctionSpecRequestParameterEdge(context).orElseThrow()
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            !context.argument.value.toOption().filterIsInstance<NullValue>().isDefined() -> {
                // add materialized value as an edge from this parameter to its source_vertex and
                // add this parameter_path to the ancestor function spec so that it can be used in
                // the request made to the source
                context.update {
                    graph(
                        context.graph
                            .putVertex(context.path, context.currentVertex)
                            .putEdge(
                                ancestorRetrievalFunctionSpecEdge.id,
                                ancestorRetrievalFunctionSpecEdge.updateSpec {
                                    addParameterVertex(context.currentVertex)
                                }
                            )
                            .putEdge(
                                context.parentPath.orNull()!!,
                                context.path,
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
                                    .build()
                            )
                    )
                }
            }
            // case 2: caller has not provided an input value for this argument so it needs to be
            // retrieved through some other source--if possible
            else -> {
                logger.debug(
                    "[ argument: { name: ${context.argument.name}, value: ${context.argument.value} } ]"
                )
                val selectedFieldToStringConverter: (SelectedField) -> CharSequence = { sf ->
                    val argumentsToString =
                        sf.arguments
                            .asSequence()
                            .joinToString(
                                ", ",
                                "{ ",
                                " }",
                                transform = { arg -> "k: ${arg.key} v: ${arg.value}" }
                            )
                    "{ name: ${sf.name}, arguments: $argumentsToString, fully_qualified_name: ${sf.fullyQualifiedName}, object_type_names: ${sf.objectTypeNames.joinToString(", ")}"
                }
                logger.debug(
                    "[ data_fetching_environment.selection_set.fields: {}",
                    context.session.dataFetchingEnvironment.selectionSet.fields
                        .asSequence()
                        .joinToString(
                            separator = ",\n",
                            prefix = "{\n",
                            postfix = "\n}",
                            transform = selectedFieldToStringConverter
                        )
                )
                context
            }
        }
    }
}
