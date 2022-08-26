package funcify.feature.materializer.service

import arrow.core.*
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.service.MaterializationGraphVertexContext.Builder
import funcify.feature.materializer.service.MaterializationGraphVertexContext.RetrievalFunctionSpec
import funcify.feature.materializer.service.MaterializationGraphVertexContext.RetrievalFunctionSpec.SpecBuilder
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
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Argument
import graphql.language.Field
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal class DefaultMaterializationGraphVertexContextFactory :
    MaterializationGraphVertexContextFactory {

    companion object {

        internal data class DefaultRetrievalFunctionSpec(
            override val dataSource: DataSource<*>,
            override val sourceVerticesByPath:
                PersistentMap<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>> =
                persistentMapOf(),
            override val parameterVerticesByPath:
                PersistentMap<SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>> =
                persistentMapOf(),
        ) : RetrievalFunctionSpec {

            companion object {
                private class DefaultSpecBuilder(
                    private var dataSource: DataSource<*>,
                    private val sourceVerticesByPathBuilder:
                        PersistentMap.Builder<
                            SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>,
                    private val parameterVerticesByPathBuilder:
                        PersistentMap.Builder<
                            SchematicPath, Either<ParameterJunctionVertex, ParameterLeafVertex>>
                ) : SpecBuilder {

                    override fun dataSource(dataSource: DataSource<*>): SpecBuilder {
                        this.dataSource = dataSource
                        return this
                    }

                    override fun addSourceVertex(
                        sourceJunctionVertex: SourceJunctionVertex
                    ): SpecBuilder {
                        this.sourceVerticesByPathBuilder.put(
                            sourceJunctionVertex.path,
                            sourceJunctionVertex.left()
                        )
                        return this
                    }

                    override fun addSourceVertex(sourceLeafVertex: SourceLeafVertex): SpecBuilder {
                        this.sourceVerticesByPathBuilder.put(
                            sourceLeafVertex.path,
                            sourceLeafVertex.right()
                        )
                        return this
                    }

                    override fun addParameterVertex(
                        parameterJunctionVertex: ParameterJunctionVertex
                    ): SpecBuilder {
                        this.parameterVerticesByPathBuilder.put(
                            parameterJunctionVertex.path,
                            parameterJunctionVertex.left()
                        )
                        return this
                    }

                    override fun addParameterVertex(
                        parameterLeafVertex: ParameterLeafVertex
                    ): SpecBuilder {
                        parameterVerticesByPathBuilder.put(
                            parameterLeafVertex.path,
                            parameterLeafVertex.right()
                        )
                        return this
                    }

                    override fun build(): RetrievalFunctionSpec {
                        // TODO: Add check that if data_source has changed, whether the vertices
                        // support the current data_source is reassessed
                        return when {
                            sourceVerticesByPathBuilder.any { (_, sjvOrSlv) ->
                                !sjvOrSlv
                                    .fold(
                                        SourceJunctionVertex::compositeAttribute,
                                        SourceLeafVertex::compositeAttribute
                                    )
                                    .getSourceAttributeByDataSource()
                                    .containsKey(dataSource.key)
                            } -> {
                                val sourceJunctionOrLeafVertexPathsWithoutDatasourceRep =
                                    sourceVerticesByPathBuilder
                                        .asSequence()
                                        .filter { (_, sjvOrSlv) ->
                                            !sjvOrSlv
                                                .fold(
                                                    SourceJunctionVertex::compositeAttribute,
                                                    SourceLeafVertex::compositeAttribute
                                                )
                                                .getSourceAttributeByDataSource()
                                                .containsKey(dataSource.key)
                                        }
                                        .map { (p, _) -> p }
                                        .joinToString(", ", "{ ", " }")
                                throw MaterializerException(
                                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                                    """source_junction_or_leaf_vertex (-ies) does 
                                        |not have a representation 
                                        |in the specified datasource for this spec: 
                                        |[ datasource.key.name: ${dataSource.key.name}, 
                                        |source_junction_or_leaf_vertex(-ies).path: 
                                        |${sourceJunctionOrLeafVertexPathsWithoutDatasourceRep}  
                                        |]""".flatten()
                                )
                            }
                            parameterVerticesByPathBuilder.any { (_, pjvOrPlv) ->
                                !pjvOrPlv
                                    .fold(
                                        ParameterJunctionVertex::compositeParameterAttribute,
                                        ParameterLeafVertex::compositeParameterAttribute
                                    )
                                    .getParameterAttributesByDataSource()
                                    .containsKey(dataSource.key)
                            } -> {
                                val parameterJunctionOrLeafVertexPathsWithoutDatasourceRep =
                                    parameterVerticesByPathBuilder
                                        .asSequence()
                                        .filter { (_, pjvOrPlv) ->
                                            !pjvOrPlv
                                                .fold(
                                                    ParameterJunctionVertex::
                                                        compositeParameterAttribute,
                                                    ParameterLeafVertex::compositeParameterAttribute
                                                )
                                                .getParameterAttributesByDataSource()
                                                .containsKey(dataSource.key)
                                        }
                                        .map { (p, _) -> p }
                                        .joinToString(", ", "{ ", " }")
                                throw MaterializerException(
                                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                                    """parameter_junction_or_leaf_vertex (-ies) does 
                                        |not have a representation 
                                        |in the specified datasource for this spec: 
                                        |[ datasource.key.name: ${dataSource.key.name}, 
                                        |parameter_junction_or_leaf_vertex(-ies).path: 
                                        |${parameterJunctionOrLeafVertexPathsWithoutDatasourceRep}  
                                        |]""".flatten()
                                )
                            }
                            else -> {
                                DefaultRetrievalFunctionSpec(
                                    dataSource,
                                    sourceVerticesByPathBuilder.build(),
                                    parameterVerticesByPathBuilder.build()
                                )
                            }
                        }
                    }
                }
            }

            override fun updateSpec(
                transformer: SpecBuilder.() -> SpecBuilder
            ): RetrievalFunctionSpec {
                return transformer
                    .invoke(
                        DefaultSpecBuilder(
                            dataSource,
                            sourceVerticesByPath.builder(),
                            parameterVerticesByPath.builder()
                        )
                    )
                    .build()
            }
        }

        internal data class DefaultMaterializationGraphVertexContext<V : SchematicVertex>(
            override val graphQLSchema: GraphQLSchema,
            override val metamodelGraph: MetamodelGraph,
            override val graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            override val materializedParameterValuesByPath: PersistentMap<SchematicPath, JsonNode> =
                persistentMapOf(),
            override val parameterIndexPathsBySourceIndexPath:
                PersistentMap<SchematicPath, PersistentSet<SchematicPath>> =
                persistentMapOf(),
            override val retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap<SchematicPath, RetrievalFunctionSpec> =
                persistentMapOf(),
            override val currentVertex: V,
            override val field: Option<Field> = none(),
            override val argument: Option<Argument> = none(),
        ) : MaterializationGraphVertexContext<V> {

            override fun <NV : SchematicVertex> update(
                transformer: Builder<V>.() -> Builder<NV>
            ): MaterializationGraphVertexContext<NV> {
                return transformer
                    .invoke(DefaultBuilder<V>(existingContext = this, vertex = currentVertex))
                    .build()
            }
        }

        internal class DefaultBuilder<V : SchematicVertex>(
            private val existingContext: DefaultMaterializationGraphVertexContext<*>,
            private var graph:
                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge> =
                existingContext.graph,
            private val materializedParameterValuesByPath:
                PersistentMap.Builder<SchematicPath, JsonNode> =
                existingContext.materializedParameterValuesByPath.builder(),
            private val parameterIndexPathsBySourceIndexPath:
                PersistentMap.Builder<SchematicPath, PersistentSet<SchematicPath>> =
                existingContext.parameterIndexPathsBySourceIndexPath.builder(),
            private val retrievalFunctionSpecByTopSourceIndexPath:
                PersistentMap.Builder<SchematicPath, RetrievalFunctionSpec> =
                existingContext.retrievalFunctionSpecByTopSourceIndexPath.builder(),
            private val vertex: V,
            private var field: Field? = existingContext.field.orNull(),
            private var argument: Argument? = existingContext.argument.orNull()
        ) : Builder<V> {

            override fun addRequestParameterEdge(
                requestParameterEdge: RequestParameterEdge
            ): Builder<V> {
                this.graph =
                    requestParameterEdge.id.first
                        .toOption()
                        .flatMap { sp ->
                            existingContext.metamodelGraph.pathBasedGraph.getVertex(sp)
                        }
                        .zip(
                            requestParameterEdge.id.second.toOption().flatMap { sp ->
                                existingContext.metamodelGraph.pathBasedGraph.getVertex(sp)
                            }
                        )
                        .map { (startVertex, endVertex) ->
                            updateRetrievalSpecsAndParameterTrackersPerVerticesAdded(
                                startVertex,
                                endVertex
                            )
                            when (requestParameterEdge) {
                                is RequestParameterEdge.MaterializedValueRequestParameterEdge -> {
                                    if (requestParameterEdge.id.first.arguments.isNotEmpty()) {
                                        materializedParameterValuesByPath.put(
                                            requestParameterEdge.id.first,
                                            requestParameterEdge.materializedJsonValue
                                        )
                                    }
                                    graph
                                        .putVertex(startVertex, SchematicVertex::path)
                                        .putVertex(endVertex, SchematicVertex::path)
                                        .putEdge(requestParameterEdge, RequestParameterEdge::id)
                                }
                                is RequestParameterEdge.DependentValueRequestParameterEdge -> {
                                    graph
                                        .putVertex(startVertex, SchematicVertex::path)
                                        .putVertex(endVertex, SchematicVertex::path)
                                        .putEdge(requestParameterEdge, RequestParameterEdge::id)
                                }
                                else -> {
                                    throw MaterializerException(
                                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                                        """unhandled request_parameter_edge type: 
                                            |[ type: ${requestParameterEdge::class.simpleName} ]
                                            |""".flatten()
                                    )
                                }
                            }
                        }
                        .getOrElse { graph }

                return this
            }

            private fun updateRetrievalSpecsAndParameterTrackersPerVerticesAdded(
                startVertex: SchematicVertex,
                endVertex: SchematicVertex,
            ) {
                sequenceOf(startVertex, endVertex).forEach { vertex: SchematicVertex ->
                    when (vertex) {
                        is SourceAttributeVertex -> {
                            val sourceJunctionOrLeafVertex:
                                Either<SourceJunctionVertex, SourceLeafVertex> =
                                when (vertex) {
                                    is SourceJunctionVertex -> vertex.left()
                                    is SourceLeafVertex -> vertex.right()
                                    else -> null
                                }!!
                            sourceJunctionOrLeafVertex
                                .fold(
                                    SourceJunctionVertex::compositeAttribute,
                                    SourceLeafVertex::compositeAttribute
                                )
                                .getSourceAttributeByDataSource()
                                .keys
                                .asSequence()
                                .map { dsKey ->
                                    dsKey to
                                        findAncestorOrKeepCurrentWithSameDataSource(
                                            vertex.path,
                                            dsKey,
                                            existingContext.metamodelGraph
                                        )
                                }
                                .map { (dsKey, ancestorOrCurrentPath) ->
                                    retrievalFunctionSpecByTopSourceIndexPath
                                        .getOrNone(ancestorOrCurrentPath)
                                        .filter { spec -> spec.dataSource.key == dsKey }
                                        .map { spec -> ancestorOrCurrentPath to spec }
                                }
                                .flatMapOptions()
                                .firstOrNull()
                                .toOption()
                                .fold(
                                    {},
                                    { (sourceIndexPath, spec) ->
                                        retrievalFunctionSpecByTopSourceIndexPath.put(
                                            sourceIndexPath,
                                            spec.updateSpec {
                                                sourceJunctionOrLeafVertex.fold(
                                                    { sjv -> addSourceVertex(sjv) },
                                                    { slv -> addSourceVertex(slv) }
                                                )
                                            }
                                        )
                                    }
                                )
                        }
                        is ParameterAttributeVertex -> {
                            val sourceIndexPath =
                                vertex.path.transform { clearArguments().clearDirectives() }
                            val parameterJunctionOrLeafVertex:
                                Either<ParameterJunctionVertex, ParameterLeafVertex> =
                                when (vertex) {
                                    is ParameterJunctionVertex -> vertex.left()
                                    is ParameterLeafVertex -> vertex.right()
                                    else -> null
                                }!!
                            when {
                                // case 1: source_index corresponding to this parameter_index
                                // already
                                // has a retreival_function_spec
                                // --> add this parameter_index to that spec
                                sourceIndexPath in retrievalFunctionSpecByTopSourceIndexPath -> {
                                    retrievalFunctionSpecByTopSourceIndexPath.put(
                                        sourceIndexPath,
                                        retrievalFunctionSpecByTopSourceIndexPath
                                            .getOrNone(sourceIndexPath)
                                            .map { spec ->
                                                spec.updateSpec {
                                                    parameterJunctionOrLeafVertex.fold(
                                                        { pjv -> addParameterVertex(pjv) },
                                                        { plv -> addParameterVertex(plv) }
                                                    )
                                                }
                                            } /* already assessed that spec is defined so non-null assertion ok */
                                            .orNull()!!
                                    )
                                }
                                // case 2: source_index corresponding to this parameter_index does
                                // not
                                // already have a retrieval function spec associated
                                // --> create the retrieval spec for this source_index and add this
                                // parameter_index to it
                                else -> {
                                    existingContext.metamodelGraph.pathBasedGraph
                                        .getVertex(sourceIndexPath)
                                        .filterIsInstance<SourceAttributeVertex>()
                                        .flatMap { sav ->
                                            sav.compositeAttribute
                                                .getSourceAttributeByDataSource()
                                                .keys
                                                .asSequence()
                                                .map { key ->
                                                    existingContext.metamodelGraph.dataSourcesByKey
                                                        .getOrNone(key)
                                                }
                                                .flatMapOptions()
                                                .firstOrNull()
                                                .toOption()
                                                .map { ds -> sav to ds }
                                        }
                                        .flatMap { (sav, ds) ->
                                            when (sav) {
                                                    is SourceJunctionVertex -> sav.left()
                                                    is SourceLeafVertex -> sav.right()
                                                    else -> null
                                                }
                                                .toOption()
                                                .map { sjvOrSlv -> sjvOrSlv to ds }
                                        }
                                        .tap { (sjvOrSlv, ds) ->
                                            addRetrievalFunctionSpecFor(sjvOrSlv, ds)
                                        }

                                    retrievalFunctionSpecByTopSourceIndexPath
                                        .getOrNone(sourceIndexPath)
                                        .map { spec ->
                                            spec.updateSpec {
                                                parameterJunctionOrLeafVertex.fold(
                                                    { pjv -> addParameterVertex(pjv) },
                                                    { plv -> addParameterVertex(plv) }
                                                )
                                            }
                                        }
                                        .tap { updatedSpec ->
                                            retrievalFunctionSpecByTopSourceIndexPath.put(
                                                sourceIndexPath,
                                                updatedSpec
                                            )
                                        }
                                }
                            }
                            parameterIndexPathsBySourceIndexPath.put(
                                sourceIndexPath,
                                parameterIndexPathsBySourceIndexPath
                                    .getOrDefault(sourceIndexPath, persistentSetOf())
                                    .add(vertex.path)
                            )
                        }
                    }
                }
            }

            override fun <
                SJV : SourceJunctionVertex, SLV : SourceLeafVertex> addRetrievalFunctionSpecFor(
                sourceVertex: Either<SJV, SLV>,
                dataSource: DataSource<*>,
            ): Builder<V> {
                val sourceVertexPath =
                    sourceVertex.fold(SchematicVertex::path, SchematicVertex::path)
                if (
                    !sourceVertex
                        .fold(
                            SourceJunctionVertex::compositeAttribute,
                            SourceLeafVertex::compositeAttribute
                        )
                        .getSourceAttributeByDataSource()
                        .containsKey(dataSource.key)
                ) {
                    throw MaterializerException(
                        MaterializerErrorResponse.UNEXPECTED_ERROR,
                        """source_junction_or_leaf_vertex does not have a 
                            |representation in the input datasource: [ 
                            |datasource.key.name: ${dataSource.key.name}, 
                            |source_junction_or_leaf_vertex.path: 
                            |${sourceVertexPath} 
                            |]""".flatten()
                    )
                }
                val ancestorOrCurrentPathWithSameDatasourceSupport: SchematicPath =
                    findAncestorOrKeepCurrentWithSameDataSource(
                        sourceVertexPath,
                        dataSource.key,
                        existingContext.metamodelGraph
                    )
                return when {
                    // case 1: already has retrieval function spec for this path for this datasource
                    retrievalFunctionSpecByTopSourceIndexPath
                        .getOrNone(sourceVertexPath)
                        .filter { spec -> spec.dataSource.key == dataSource.key }
                        .isDefined() -> {
                        this
                    }
                    // case 2: has an ancestor path with same datasource support
                    // and has a vertex in the metamodel
                    ancestorOrCurrentPathWithSameDatasourceSupport != sourceVertexPath &&
                        existingContext.metamodelGraph.pathBasedGraph
                            .getVertex(ancestorOrCurrentPathWithSameDatasourceSupport)
                            .isDefined() -> {
                        when {
                            // case 2.1: There is not already a spec for this ancestor path
                            // --> create spec and add both vertices to it
                            !retrievalFunctionSpecByTopSourceIndexPath.containsKey(
                                ancestorOrCurrentPathWithSameDatasourceSupport
                            ) -> {
                                val ancestorSourceVertex:
                                    Either<SourceJunctionVertex, SourceLeafVertex> =
                                    when (
                                        val v =
                                            existingContext.metamodelGraph.pathBasedGraph
                                                .getVertex(
                                                    ancestorOrCurrentPathWithSameDatasourceSupport
                                                ) /* already checked that this is defined so non-null assertion ok */
                                                .orNull()!!
                                    ) {
                                        is SourceJunctionVertex -> v.left()
                                        is SourceLeafVertex -> v.right()
                                        else -> null
                                    }!!
                                retrievalFunctionSpecByTopSourceIndexPath.put(
                                    ancestorOrCurrentPathWithSameDatasourceSupport,
                                    DefaultRetrievalFunctionSpec(
                                        dataSource,
                                        persistentMapOf(
                                            ancestorOrCurrentPathWithSameDatasourceSupport to
                                                ancestorSourceVertex,
                                            sourceVertexPath to sourceVertex
                                        )
                                    )
                                )
                                this
                            }
                            // case 2.2: There is already a spec for this ancestor path
                            // --> add source_vertex to that spec
                            else -> {
                                retrievalFunctionSpecByTopSourceIndexPath.put(
                                    ancestorOrCurrentPathWithSameDatasourceSupport,
                                    retrievalFunctionSpecByTopSourceIndexPath
                                        .getOrNone(ancestorOrCurrentPathWithSameDatasourceSupport)
                                        .map { spec ->
                                            spec.updateSpec {
                                                sourceVertex.fold(
                                                    { sjv -> addSourceVertex(sjv) },
                                                    { slv -> addSourceVertex(slv) }
                                                )
                                            }
                                        }
                                        .orNull()!!
                                )
                                this
                            }
                        }
                    }
                    // case 3: does not have an ancestor path to vertex with same datasource support
                    // and does not already have a spec created
                    else -> {
                        retrievalFunctionSpecByTopSourceIndexPath.put(
                            sourceVertexPath,
                            DefaultRetrievalFunctionSpec(
                                dataSource,
                                persistentMapOf(sourceVertexPath to sourceVertex)
                            )
                        )
                        this
                    }
                }
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

            override fun addRetrievalFunctionSpecFor(
                sourceJunctionVertex: SourceJunctionVertex,
                dataSource: DataSource<*>
            ): Builder<V> {
                return addRetrievalFunctionSpecFor(sourceJunctionVertex.left(), dataSource)
            }

            override fun addRetrievalFunctionSpecFor(
                sourceLeafVertex: SourceLeafVertex,
                dataSource: DataSource<*>
            ): Builder<V> {
                return addRetrievalFunctionSpecFor(sourceLeafVertex.right(), dataSource)
            }

            override fun <NV : SchematicVertex> nextVertex(nextVertex: NV): Builder<NV> {
                return DefaultBuilder<NV>(
                    existingContext = existingContext,
                    graph = graph,
                    materializedParameterValuesByPath = materializedParameterValuesByPath,
                    parameterIndexPathsBySourceIndexPath = parameterIndexPathsBySourceIndexPath,
                    retrievalFunctionSpecByTopSourceIndexPath =
                        retrievalFunctionSpecByTopSourceIndexPath,
                    vertex = nextVertex,
                    field = null,
                    argument = null,
                )
            }

            override fun <NV : SchematicVertex> nextVertex(
                nextVertex: NV,
                field: Field
            ): Builder<NV> {
                return DefaultBuilder<NV>(
                    existingContext = existingContext,
                    graph = graph,
                    materializedParameterValuesByPath = materializedParameterValuesByPath,
                    parameterIndexPathsBySourceIndexPath = parameterIndexPathsBySourceIndexPath,
                    retrievalFunctionSpecByTopSourceIndexPath =
                        retrievalFunctionSpecByTopSourceIndexPath,
                    vertex = nextVertex,
                    field = field,
                    argument = null,
                )
            }

            override fun <NV : SchematicVertex> nextVertex(
                nextVertex: NV,
                argument: Argument
            ): Builder<NV> {
                return DefaultBuilder<NV>(
                    existingContext = existingContext,
                    graph = graph,
                    materializedParameterValuesByPath = materializedParameterValuesByPath,
                    parameterIndexPathsBySourceIndexPath = parameterIndexPathsBySourceIndexPath,
                    retrievalFunctionSpecByTopSourceIndexPath =
                        retrievalFunctionSpecByTopSourceIndexPath,
                    vertex = nextVertex,
                    field = null,
                    argument = argument,
                )
            }

            override fun build(): MaterializationGraphVertexContext<V> {
                return DefaultMaterializationGraphVertexContext<V>(
                    graphQLSchema = existingContext.graphQLSchema,
                    metamodelGraph = existingContext.metamodelGraph,
                    graph = graph,
                    materializedParameterValuesByPath = materializedParameterValuesByPath.build(),
                    parameterIndexPathsBySourceIndexPath =
                        parameterIndexPathsBySourceIndexPath.build(),
                    retrievalFunctionSpecByTopSourceIndexPath =
                        retrievalFunctionSpecByTopSourceIndexPath.build(),
                    currentVertex = vertex,
                    field = field.toOption(),
                    argument = argument.toOption()
                )
            }
        }
    }

    override fun createSourceRootVertexContext(
        sourceRootVertex: SourceRootVertex,
        metamodelGraph: MetamodelGraph,
        materializationSchema: GraphQLSchema
    ): MaterializationGraphVertexContext<SourceRootVertex> {
        return DefaultMaterializationGraphVertexContext<SourceRootVertex>(
            graphQLSchema = materializationSchema,
            metamodelGraph = metamodelGraph,
            graph = PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
            currentVertex = sourceRootVertex
        )
    }
}
