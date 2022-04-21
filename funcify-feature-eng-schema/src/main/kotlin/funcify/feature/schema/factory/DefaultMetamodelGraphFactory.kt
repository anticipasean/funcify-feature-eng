package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.or
import arrow.core.some
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
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
                            """
                            |data_source already added by same name: 
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
                                        .parallel()
                                        .map { entry ->
                                            Try.attempt {
                                                createNewOrUpdateExistingSchematicVertex(
                                                        dataSource = dataSource,
                                                        existingSchematicVerticesByPath =
                                                            schematicVerticesByPath,
                                                        entry = entry
                                                    )
                                                    .stream()
                                            }
                                                .peekIfFailure { thr: Throwable ->
                                                    logger.error(
                                                        """add_data_source: [ status: failed ] 
                                                           |[ error: { type: ${thr::class.qualifiedName}, 
                                                           |message: "${thr.message}" } ]""".flattenIntoOneLine()
                                                    )
                                                }
                                        }
                                        .reduce { st1, st2 ->
                                            st1.zip(st2) { s1, s2 -> Stream.concat(s1, s2) }
                                        }
                                        .orElseGet { Try.success(Stream.empty()) }
                                        .map { stream ->
                                            schematicVerticesByPath.putAll(
                                                stream.reducePairsToPersistentMap()
                                            )
                                        }
                                }
                        )
                    }
                }
            }

            private fun <SI : SourceIndex> createNewOrUpdateExistingSchematicVertex(
                dataSource: DataSource<SI>,
                existingSchematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>,
                entry: Map.Entry<SchematicPath, ImmutableSet<SI>>
            ): Option<Pair<SchematicPath, SchematicVertex>> {
                val transformedSourcePath: SchematicPath =
                    sourcePathTransformer.transformSourcePathToSchematicPathForDataSource(
                        sourcePath = entry.key,
                        dataSource = dataSource
                    )
                return when (val existingVertex: SchematicVertex? =
                        existingSchematicVerticesByPath[transformedSourcePath]
                ) {
                    null -> {
                        entry
                            .value
                            .stream()
                            .reduce(
                                none<SchematicVertex>(),
                                { vOpt: Option<SchematicVertex>, si: SI ->
                                    vOpt.fold(
                                            {
                                                schematicVertexFactory
                                                    .createVertexForPath(transformedSourcePath)
                                                    .forSourceIndex<SI>(si)
                                                    .onDataSource(dataSource)
                                            },
                                            { ev ->
                                                schematicVertexFactory
                                                    .createVertexForPath(transformedSourcePath)
                                                    .fromExistingVertex(ev)
                                                    .forSourceIndex<SI>(si)
                                                    .onDataSource(dataSource)
                                            }
                                        )
                                        .orElseThrow()
                                        .some()
                                },
                                { vOpt1, vOpt2 ->
                                    // not parallel so either leaf node is the same
                                    vOpt2.or(vOpt1)
                                }
                            )
                            .map { v -> transformedSourcePath to v }
                    }
                    else -> {
                        entry
                            .value
                            .stream()
                            .reduce(
                                existingVertex,
                                { ev: SchematicVertex, si: SI ->
                                    schematicVertexFactory
                                        .createVertexForPath(transformedSourcePath)
                                        .fromExistingVertex(ev)
                                        .forSourceIndex<SI>(si)
                                        .onDataSource(dataSource)
                                        .orElseThrow()
                                },
                                { _, v2 ->
                                    // not parallel so either leaf node is the same
                                    v2
                                }
                            )
                            .some()
                            .map { v -> transformedSourcePath to v }
                    }
                }
            }

            override fun build(): Try<MetamodelGraph> {
                return dataSourcesByNameAttempt.zip(schematicVerticesByPathAttempt) {
                    dataSourcesByName: PersistentMap<String, DataSource<*>>,
                    schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex> ->
                    DefaultMetamodelGraph(
                        dataSourcesByName = dataSourcesByName,
                        schematicVerticesByPath = schematicVerticesByPath
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
