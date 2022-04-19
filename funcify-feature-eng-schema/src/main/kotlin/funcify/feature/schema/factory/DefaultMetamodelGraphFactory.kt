package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.MetamodelGraphFactory
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal class DefaultMetamodelGraphFactory : MetamodelGraphFactory {

    companion object {

        internal data class DefaultDataSourceSpec(
            val dataSources: PersistentList<DataSource<*>> = persistentListOf(),
            val schematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex> =
                persistentMapOf()
        ) : MetamodelGraphFactory.DataSourceSpec {

            override fun <SI : SourceIndex> includingDataSource(
                dataSource: DataSource<SI>
            ): MetamodelGraphFactory.DataSourceSpec {
                return DefaultDataSourceSpec(
                    dataSources = dataSources.add(dataSource),
                    schematicVerticesByPath =
                        schematicVerticesByPath.putAll(
                            dataSource
                                .sourceMetamodel
                                .sourceIndicesByPath
                                .streamEntries()
                                .parallel()
                                .map { entry ->
                                    createNewOrUpdateExistingSchematicVertex(
                                        schematicVerticesByPath,
                                        entry
                                    )
                                }
                                .reducePairsToPersistentMap()
                        )
                )
            }

            private fun createNewOrUpdateExistingSchematicVertex(
                existingSchematicVerticesByPath: PersistentMap<SchematicPath, SchematicVertex>,
                entry: Map.Entry<SchematicPath, ImmutableSet<SourceIndex>>
            ): Pair<SchematicPath, SchematicVertex> {
                return when (val existingVertex: SchematicVertex? =
                        existingSchematicVerticesByPath[entry.key]
                ) {
                    null -> {
                        TODO()
                    }
                    else -> {
                        TODO()
                    }
                }
            }

            override fun build(): Try<MetamodelGraph> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun createMetamodelGraph(): MetamodelGraphFactory.DataSourceSpec {
        return DefaultDataSourceSpec()
    }
}
