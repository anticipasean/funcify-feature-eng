package funcify.feature.schema.factory

import arrow.core.getOrNone
import arrow.core.left
import arrow.core.right
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultMetamodelGraphFactory(
    val schematicVertexFactory: SchematicVertexFactory,
    val sourcePathTransformer: SourcePathTransformer
) : MetamodelGraphFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultMetamodelGraphFactory>()

        internal data class DefaultDataSourceSpec(
            val schematicVertexFactory: SchematicVertexFactory,
            val sourcePathTransformer: SourcePathTransformer,
            val dataSourcesByNameAttempt: Try<PersistentMap<String, DataSource<*>>> =
                Try.success(persistentMapOf()),
            val schematicVerticesByPathAttempt: Try<PersistentMap<SchematicPath, SchematicVertex>> =
                Try.success(persistentMapOf())
        ) : MetamodelGraph.Builder {

            override fun <SI : SourceIndex> addDataSource(
                dataSource: DataSource<SI>
            ): MetamodelGraph.Builder {
                logger.debug(
                    "add_data_source: [ name: ${dataSource.name}, type: ${dataSource.sourceType} ]"
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
                            sourcePathTransformer = sourcePathTransformer,
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
                            sourcePathTransformer = sourcePathTransformer,
                            dataSourcesByNameAttempt =
                                dataSourcesByNameAttempt.map { dsMap ->
                                    dsMap.put(dataSource.name, dataSource)
                                },
                            schematicVerticesByPathAttempt =
                                schematicVerticesByPathAttempt.flatMap { schematicVerticesByPath ->
                                    dataSource
                                        .sourceMetamodel
                                        .sourceIndicesByPath
                                        .streamEntries()
                                        .flatMap(::expandAndIncludeChildSourceIndicesRecursively)
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

            private fun <SI : SourceIndex> expandAndIncludeChildSourceIndicesRecursively(
                entry: Map.Entry<SchematicPath, ImmutableSet<SI>>
            ): Stream<SI> {
                return entry
                    .value
                    .stream()
                    .flatMap { si: SI ->
                        TraversalFunctions.recurseWithStream(si) { inputSI: SI ->
                            if (inputSI is SourceContainerType<*>) {
                                Stream.concat(
                                    inputSI
                                        .sourceAttributes
                                        .stream()
                                        .map { sa ->
                                            /**
                                             * All source attrs must be subtypes of SI in this
                                             * context
                                             */
                                            @Suppress("UNCHECKED_CAST") //
                                            sa as SI
                                        }
                                        .map { saSI: SI -> saSI.left() },
                                    Stream.of(inputSI.right())
                                )
                            } else {
                                Stream.of(inputSI.right())
                            }
                        }
                    }
                    .sorted(Comparator.comparing { si: SI -> si.sourcePath.pathSegments.size })
            }

            private fun <SI : SourceIndex> createNewOrUpdateExistingSchematicVertex(
                dataSource: DataSource<SI>,
                existingSchematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>,
                sourceIndex: SI
            ): Pair<SchematicPath, SchematicVertex> {
                val transformedSourcePath: SchematicPath =
                    sourcePathTransformer.transformSourcePathToSchematicPathForDataSource(
                        sourcePath = sourceIndex.sourcePath,
                        dataSource = dataSource
                    )
                return when (val existingVertex: SchematicVertex? =
                        existingSchematicVerticesByPath[transformedSourcePath]
                ) {
                    null -> {
                        transformedSourcePath to
                            schematicVertexFactory
                                .createVertexForPath(transformedSourcePath)
                                .forSourceIndex<SI>(sourceIndex)
                                .onDataSource(dataSource)
                                .orElseThrow()
                    }
                    else -> {
                        transformedSourcePath to
                            schematicVertexFactory
                                .createVertexForPath(transformedSourcePath)
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
                        _: PersistentMap<String, DataSource<*>>,
                        schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex> ->
                        DefaultMetamodelGraph(
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
            }
        }
    }

    override fun builder(): MetamodelGraph.Builder {
        return DefaultDataSourceSpec(
            schematicVertexFactory = schematicVertexFactory,
            sourcePathTransformer = sourcePathTransformer
        )
    }
}
