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
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultMetamodelGraphFactory(
    val schematicVertexFactory: SchematicVertexFactory,
    val sourcePathTransformer: SourcePathTransformer
) : MetamodelGraphFactory {

    companion object {

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
                                dataSourcesByNameAttempt.flatMap { _ ->
                                    Try.failure(
                                        SchemaException(
                                            SchemaErrorResponse.UNIQUE_CONSTRAINT_VIOLATION,
                                            message
                                        )
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
                                    Try.attempt {
                                        schematicVerticesByPath.putAll(
                                            dataSource
                                                .sourceMetamodel
                                                .sourceIndicesByPath
                                                .streamEntries()
                                                .parallel()
                                                .map { entry ->
                                                    createNewOrUpdateExistingSchematicVertex(
                                                        dataSource = dataSource,
                                                        existingSchematicVerticesByPath =
                                                            schematicVerticesByPath,
                                                        entry = entry
                                                    )
                                                }
                                                .flatMap { opt -> opt.stream() }
                                                .reducePairsToPersistentMap()
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
