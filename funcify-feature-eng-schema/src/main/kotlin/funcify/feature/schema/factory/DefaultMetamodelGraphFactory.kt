package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.none
import arrow.core.or
import arrow.core.some
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultMetamodelGraphFactory(
    val schematicVertexFactory: SchematicVertexFactory,
    val sourcePathTransformer: SourcePathTransformer
) : MetamodelGraphFactory {

    companion object {

        internal data class DefaultDataSourceSpec(
            val schematicVertexFactory: SchematicVertexFactory,
            val sourcePathTransformer: SourcePathTransformer,
            val dataSources: PersistentList<DataSource<*>> = persistentListOf(),
            val schematicVerticesByPathAttempt: Try<PersistentMap<SchematicPath, SchematicVertex>> =
                Try.success(persistentMapOf())
        ) : MetamodelGraphFactory.DataSourceSpec {

            override fun <SI : SourceIndex> includingDataSource(
                dataSource: DataSource<SI>
            ): MetamodelGraphFactory.DataSourceSpec {
                return DefaultDataSourceSpec(
                    schematicVertexFactory = schematicVertexFactory,
                    sourcePathTransformer = sourcePathTransformer,
                    dataSources = dataSources.add(dataSource),
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
                                    // not parallel so only right leaf should be Some
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
                                    // not parallel so only right leaf should be Some
                                    v2
                                }
                            )
                            .some()
                            .map { v -> transformedSourcePath to v }
                    }
                }
            }

            override fun build(): Try<MetamodelGraph> {
                return schematicVerticesByPathAttempt.map { schematicVerticesByPath ->
                    DefaultMetamodelGraph(schematicVerticesByPath = schematicVerticesByPath)
                }
            }
        }
    }

    override fun createMetamodelGraph(): MetamodelGraphFactory.DataSourceSpec {
        return DefaultDataSourceSpec(
            schematicVertexFactory = schematicVertexFactory,
            sourcePathTransformer = sourcePathTransformer
        )
    }
}
