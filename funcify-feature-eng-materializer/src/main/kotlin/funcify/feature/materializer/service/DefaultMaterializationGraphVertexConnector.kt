package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.json.GraphQLValueToJsonNodeConverter
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdgeFactory
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
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
        context: MaterializationGraphVertexContext<SourceRootVertex>
    ): MaterializationGraphVertexContext<*> {
        logger.info(
            "connect_source_root_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return context
    }

    override fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        context: MaterializationGraphVertexContext<V>
    ): MaterializationGraphVertexContext<*> {
        logger.info(
            "connect_source_junction_or_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        val sourceJunctionOrLeafVertex: Either<SourceJunctionVertex, SourceLeafVertex> =
            when (val v = context.currentVertex) {
                is SourceJunctionVertex -> v.left()
                is SourceLeafVertex -> v.right()
                else -> null
            }!!
        return when {
            // case 1: current source_index already has a retrieval_function_spec associated with it
            // --> continue
            context.path in context.retrievalFunctionSpecByTopSourceIndexPath -> {
                context
            }
            // case 2: current source_index does not already have a retrieval_function_spec
            else -> {
                val selectedDatasource: DataSource<*> =
                    selectDataSourceForSourceAttributeVertex(
                        context.currentVertex,
                        context.metamodelGraph
                    )
                val currentOrAncestorPathWithSameDataSource =
                    findAncestorOrKeepCurrentWithSameDataSource(
                        context.path,
                        selectedDatasource.key,
                        context.metamodelGraph
                    )
                val nearestLastUpdatedAttributeEdge: Option<RequestParameterEdge> =
                    createNearestLastUpdatedAttributeEdgeIfNotPresentInExpectedAncestorSpec(context)
                // case 2.1: source_index does not have a retrieval_function_spec but does not have
                // an ancestor that shares the same datasource and therefore must have its own
                // retrieval_function_spec
                // --> create retrieval_function_spec and connect any parameter_vertices associated
                // with this spec
                if (currentOrAncestorPathWithSameDataSource == context.path) {
                    context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                        .getOrNone(context.path)
                        .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                        .fold(::emptySequence, ::identity)
                        .fold(
                            context.update {
                                addRetrievalFunctionSpecFor(
                                        sourceJunctionOrLeafVertex,
                                        selectedDatasource
                                    )
                                    .let { bldr ->
                                        nearestLastUpdatedAttributeEdge.tap { edge ->
                                            bldr.addRequestParameterEdge(edge)
                                        }
                                        bldr
                                    }
                            } as MaterializationGraphVertexContext<*>
                        ) { ctx, parameterAttributeVertex ->
                            connectSchematicVertex(
                                ctx.update { nextVertex(parameterAttributeVertex) }
                            )
                        }
                } else {
                    // case 2.2: source_index does not have a retrieval_function_spec and does have
                    // an ancestor that shares the same datasource
                    // the value for this source_index therefore should be extracted from its
                    // ancestor result_map
                    // --> connect this vertex to its ancestor spec and connect any parameters
                    // associated with this vertex
                    context.metamodelGraph.parameterAttributeVerticesBySourceAttributeVertexPaths
                        .getOrNone(context.path)
                        .map(ImmutableSet<ParameterAttributeVertex>::asSequence)
                        .fold(::emptySequence, ::identity)
                        .fold(
                            context.update {
                                addRequestParameterEdge(
                                        requestParameterEdgeFactory
                                            .builder()
                                            .fromPathToPath(
                                                currentOrAncestorPathWithSameDataSource,
                                                context.path
                                            )
                                            .dependentExtractionFunction { resultMap ->
                                                resultMap.getOrNone(context.path)
                                            }
                                            .build()
                                    )
                                    .let { bldr ->
                                        nearestLastUpdatedAttributeEdge.tap { edge ->
                                            bldr.addRequestParameterEdge(edge)
                                        }
                                        bldr
                                    }
                            } as MaterializationGraphVertexContext<*>
                        ) { ctx, parameterAttributeVertex ->
                            connectSchematicVertex(
                                ctx.update { nextVertex(parameterAttributeVertex) }
                            )
                        }
                }
            }
        }
    }

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

    private fun <
        V : SourceAttributeVertex
    > createNearestLastUpdatedAttributeEdgeIfNotPresentInExpectedAncestorSpec(
        context: MaterializationGraphVertexContext<V>
    ): Option<RequestParameterEdge> {
        return context.metamodelGraph.lastUpdatedTemporalAttributePathRegistry
            .findNearestLastUpdatedTemporalAttributePathRelative(context.path)
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
                                resultMap.getOrNone(sav.path)
                            }
                            .build()
                    }
            }
    }

    override fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        context: MaterializationGraphVertexContext<V>
    ): MaterializationGraphVertexContext<*> {
        logger.debug(
            "connect_parameter_junction_or_leaf_vertex: [ context.vertex_path: ${context.currentVertex.path} ]"
        )
        return when {
            // case 1: caller has provided an input value for this argument so it need not be
            // retrieved through any other means
            context.argument.isDefined() &&
                !context.argument
                    .map(Argument::getValue)
                    .filterIsInstance<NullValue>()
                    .isDefined() -> {
                context.update {
                    addRequestParameterEdge(
                        requestParameterEdgeFactory
                            .builder()
                            .fromPathToPath(
                                // parameter --> source
                                context.path,
                                context.path.transform { clearArguments().clearDirectives() }
                            )
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
                            .build()
                    )
                }
            }
            // case 2: caller has not provided an input value for this argument so it needs to be
            // retrieved through some other source--if possible
            else -> {
                val sourceAttributeVertexWithSameNameOrAlias: Option<SourceAttributeVertex> =
                    findSourceAttributeVertexWithSameNameInSameDomain(context)
                        .orElse { findSourceAttributeVertexByAliasReferenceInSameDomain(context) }
                        .orElse { findSourceAttributeVertexWithSameNameInDifferentDomain(context) }
                        .orElse {
                            findSourceAttributeVertexByAliasReferenceInDifferentDomain(context)
                        }
                when {
                    // case 2.1: source is another source_attribute_vertex by same name or alias
                    sourceAttributeVertexWithSameNameOrAlias.isDefined() -> {
                        context.update {
                            addRequestParameterEdge(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(
                                            // source_attr_vertex to this parameter_index
                                            // its value depends on that source_index value
                                            sourceAttributeVertexWithSameNameOrAlias
                                                .map(SourceAttributeVertex::path)
                                                .orNull()!!,
                                            context.path
                                        )
                                        .dependentExtractionFunction { resultMap ->
                                            resultMap.values.firstOrNone()
                                        }
                                        .build()
                                )
                                .addRequestParameterEdge(
                                    requestParameterEdgeFactory
                                        .builder()
                                        .fromPathToPath(
                                            context.path,
                                            context.path.transform {
                                                clearArguments().clearDirectives()
                                            }
                                        )
                                        .dependentExtractionFunction { resultMap ->
                                            resultMap.values.firstOrNone()
                                        }
                                        .build()
                                )
                        }
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
                        context.update {
                            addRequestParameterEdge(
                                requestParameterEdgeFactory
                                    .builder()
                                    .fromPathToPath(
                                        context.path,
                                        context.path.transform {
                                            clearArguments().clearDirectives()
                                        }
                                    )
                                    .materializedValue(materializedDefaultJsonValue)
                                    .build()
                            )
                        }
                    }
                    else -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.UNEXPECTED_ERROR,
                            """parameter_junction_or_leaf_vertex could not be mapped to 
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
}
