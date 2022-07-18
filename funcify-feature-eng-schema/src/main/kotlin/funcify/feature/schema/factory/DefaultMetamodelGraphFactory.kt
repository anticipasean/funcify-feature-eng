package funcify.feature.schema.factory

import arrow.core.getOrNone
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.DefaultSchematicVertexGraphRemappingContext
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingContext
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultMetamodelGraphFactory(
    val schematicVertexFactory: SchematicVertexFactory,
    val schematicVertexGraphRemappingStrategy: SchematicVertexGraphRemappingStrategy
) : MetamodelGraphFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultMetamodelGraphFactory>()

        internal data class DefaultDataSourceSpec(
            val schematicVertexFactory: SchematicVertexFactory,
            val schematicVertexGraphRemappingStrategy: SchematicVertexGraphRemappingStrategy,
            val dataSourcesByNameAttempt: Try<PersistentMap<String, DataSource<*>>> =
                Try.success(persistentMapOf()),
            val schematicVerticesByPathAttempt: Try<PersistentMap<SchematicPath, SchematicVertex>> =
                Try.success(persistentMapOf()),
        ) : MetamodelGraph.Builder {

            override fun <SI : SourceIndex<SI>> addDataSource(
                dataSource: DataSource<SI>
            ): MetamodelGraph.Builder {
                logger.debug(
                    "add_data_source: [ name: ${dataSource.name}, type: ${dataSource.dataSourceType} ]"
                )
                return when {
                    /**
                     * Case 1: Failure to add datasource or create a or update an existing schematic
                     * vertex occurred => return current instance
                     */
                    dataSourcesByNameAttempt.isFailure() ||
                        schematicVerticesByPathAttempt.isFailure() -> {
                        this
                    }
                    /**
                     * Case 2: Data source with that name has already been defined => return
                     * instance with unique constraint violation error
                     */
                    dataSourcesByNameAttempt
                        .map { dsMap -> dsMap.getOrNone(dataSource.name).isDefined() }
                        .orElse(false) -> {
                        val message =
                            """data_source already added by same name: 
                            |[ name: ${dataSource.name} ]
                            |""".flattenIntoOneLine()
                        DefaultDataSourceSpec(
                            schematicVertexFactory = schematicVertexFactory,
                            schematicVertexGraphRemappingStrategy =
                                schematicVertexGraphRemappingStrategy,
                            dataSourcesByNameAttempt =
                                dataSourcesByNameAttempt
                                    .flatMap<PersistentMap<String, DataSource<*>>> { _ ->
                                        Try.failure(
                                            SchemaException(
                                                SchemaErrorResponse.UNIQUE_CONSTRAINT_VIOLATION,
                                                message
                                            )
                                        )
                                    }
                                    .peekIfFailure { thr: Throwable ->
                                        logger.error(
                                            """add_data_source: [ status: failed ] 
                                               |[ error: { type: ${thr::class.qualifiedName}, 
                                               |message: "$message" } ]""".flattenIntoOneLine()
                                        )
                                    },
                            schematicVerticesByPathAttempt = schematicVerticesByPathAttempt
                        )
                    }
                    /**
                     * Case 3: Proceed in creating a new or updating an existing schematic vertex
                     */
                    else -> {
                        DefaultDataSourceSpec(
                            schematicVertexFactory = schematicVertexFactory,
                            schematicVertexGraphRemappingStrategy =
                                schematicVertexGraphRemappingStrategy,
                            dataSourcesByNameAttempt =
                                dataSourcesByNameAttempt.map { dsMap ->
                                    dsMap.put(dataSource.name, dataSource)
                                },
                            schematicVerticesByPathAttempt =
                                schematicVerticesByPathAttempt.flatMap { schematicVerticesByPath ->
                                    dataSource.sourceMetamodel.sourceIndicesByPath
                                        .streamEntries()
                                        .flatMap { (_, sourceIndexSet) -> sourceIndexSet.stream() }
                                        .sorted { si1, si2 ->
                                            si1.sourcePath.compareTo(si2.sourcePath)
                                        }
                                        .reduce(
                                            Try.success(schematicVerticesByPath),
                                            { svpAttempt, sourceIndex ->
                                                svpAttempt
                                                    .map { svp ->
                                                        createNewOrUpdateExistingSchematicVertex(
                                                                dataSource = dataSource,
                                                                existingSchematicVerticesByPath =
                                                                    svp,
                                                                sourceIndex = sourceIndex
                                                            )
                                                            .let { pair ->
                                                                svp.put(pair.first, pair.second)
                                                            }
                                                    }
                                                    .peekIfFailure { thr: Throwable ->
                                                        logger.error(
                                                            """add_data_source: [ status: failed ] 
                                                                                 |[ error: { type: ${thr::class.qualifiedName}, 
                                                                                 |message: "${thr.message}" } ]""".flattenIntoOneLine()
                                                        )
                                                    }
                                            },
                                            { _, t2 ->
                                                // sequential so leaf nodes are the same
                                                t2
                                            }
                                        )
                                }
                        )
                    }
                }
            }

            private fun <SI : SourceIndex<SI>> createNewOrUpdateExistingSchematicVertex(
                dataSource: DataSource<SI>,
                existingSchematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>,
                sourceIndex: SI
            ): Pair<SchematicPath, SchematicVertex> {
                val sourcePath: SchematicPath = sourceIndex.sourcePath
                return when (
                    val existingVertex: SchematicVertex? =
                        existingSchematicVerticesByPath[sourcePath]
                ) {
                    null -> {
                        sourcePath to
                            schematicVertexFactory
                                .createVertexForPath(sourcePath)
                                .forSourceIndex<SI>(sourceIndex)
                                .onDataSource(dataSource)
                                .orElseThrow()
                    }
                    else -> {
                        sourcePath to
                            schematicVertexFactory
                                .createVertexForPath(sourcePath)
                                .fromExistingVertex(existingVertex)
                                .forSourceIndex<SI>(sourceIndex)
                                .onDataSource(dataSource)
                                .orElseThrow()
                    }
                }
            }

            override fun build(): Try<MetamodelGraph> {
                return dataSourcesByNameAttempt
                    .zip(schematicVerticesByPathAttempt) {
                        dsByName: PersistentMap<String, DataSource<*>>,
                        schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex> ->
                        DefaultMetamodelGraph(
                            dataSourcesByKey =
                                dsByName.values.fold(persistentMapOf()) { pm, ds ->
                                    if (pm.containsKey(ds.key)) {
                                        throw SchemaException(
                                            SchemaErrorResponse.UNIQUE_CONSTRAINT_VIOLATION,
                                            """datasource with key: [ key: ${ds.key} ] 
                                                |has already been added to 
                                                |metamodel_graph.datasource_by_key map
                                                |""".flattenIntoOneLine()
                                        )
                                    } else {
                                        pm.put(ds.key, ds)
                                    }
                                },
                            pathBasedGraph =
                                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<
                                        SchematicPath, SchematicVertex, SchematicEdge>()
                                    .putAllVertices(schematicVerticesByPath)
                        )
                    }
                    .peekIfFailure { thr: Throwable ->
                        logger.error(
                            """build: [ status: failed ] 
                               |[ error: { type: ${thr::class.qualifiedName}, 
                               |message: ${thr.message} } ]
                            """.flattenIntoOneLine()
                        )
                    }
                    .map { metaModelGraph ->
                        logger.debug(
                            """apply_remapping_strategy_to_metamodel_graph: 
                            |[ vertices.size: ${metaModelGraph.pathBasedGraph.vertices.size} 
                            |]""".flattenIntoOneLine()
                        )
                        metaModelGraph.pathBasedGraph.vertices
                            .asSequence()
                            .sortedBy { sv -> sv.path }
                            .fold(
                                (DefaultSchematicVertexGraphRemappingContext(
                                    metaModelGraph.dataSourcesByKey,
                                    schematicVertexFactory,
                                    metaModelGraph.pathBasedGraph
                                )
                                    as SchematicVertexGraphRemappingContext)
                            ) { ctx, schematicVertex ->
                                if (
                                    schematicVertexGraphRemappingStrategy.canBeAppliedTo(
                                        ctx,
                                        schematicVertex
                                    )
                                ) {
                                    schematicVertexGraphRemappingStrategy
                                        .applyToVertexInContext(ctx, schematicVertex)
                                        .orElseThrow()
                                } else {
                                    ctx
                                }
                            }
                            .let { remappingContext ->
                                metaModelGraph.copy(
                                    pathBasedGraph = remappingContext.pathBasedGraph
                                )
                            }
                    }
            }
        }
    }

    override fun builder(): MetamodelGraph.Builder {
        return DefaultDataSourceSpec(
            schematicVertexFactory = schematicVertexFactory,
            schematicVertexGraphRemappingStrategy = schematicVertexGraphRemappingStrategy
        )
    }
}
