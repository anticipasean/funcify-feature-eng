package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.CompositeAttribute
import funcify.feature.schema.CompositeContainerType
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.SchematicVertexFactory
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.graph.RootVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

class DefaultSchematicVertexFactory : SchematicVertexFactory {

    companion object {

        private data class DefaultLeafVertex(
            override val path: SchematicPath,
            override val compositeAttribute: CompositeAttribute
        ) : LeafVertex {}

        private data class DefaultRootVertex(
            override val path: SchematicPath,
            override val compositeContainerType: CompositeContainerType
        ) : RootVertex {}

        private data class DefaultJunctionVertex(
            override val path: SchematicPath,
            override val compositeContainerType: CompositeContainerType,
            override val compositeAttribute: CompositeAttribute
        ) : JunctionVertex {}

        private data class DefaultCompositeContainerType(
            override val conventionalName: ConventionalName,
            private val sourceContainerTypesByDataSource:
                PersistentMap<DataSource<*>, SourceContainerType<*>> =
                persistentMapOf()
        ) : CompositeContainerType {

            fun <SI : SourceIndex, SCT : SourceContainerType<SA>, SA> put(
                dataSource: DataSource<SI>,
                sourceContainerType: SCT
            ): DefaultCompositeContainerType where SA : SI {
                return DefaultCompositeContainerType(
                    conventionalName = conventionalName,
                    sourceContainerTypesByDataSource =
                        sourceContainerTypesByDataSource.put(dataSource, sourceContainerType)
                )
            }

            override fun getSourceContainerTypeByDataSource():
                ImmutableMap<DataSource<*>, SourceContainerType<*>> {
                return sourceContainerTypesByDataSource
            }
        }

        private data class DefaultCompositeAttribute(
            override val conventionalName: ConventionalName,
            private val sourceAttributesByDataSource:
                PersistentMap<DataSource<*>, SourceAttribute> =
                persistentMapOf()
        ) : CompositeAttribute {

            fun <SI : SourceIndex, SCT : SourceContainerType<SA>, SA> put(
                dataSource: DataSource<SI>,
                sourceAttribute: SA
            ): DefaultCompositeAttribute where SA : SI {
                return DefaultCompositeAttribute(
                    conventionalName = conventionalName,
                    sourceAttributesByDataSource =
                        sourceAttributesByDataSource.put(
                            dataSource,
                            /* cast is sound since SA must
                             * be a source attribute per source
                             * container type's bounds for SA
                             */
                            sourceAttribute as SourceAttribute
                        )
                )
            }
            override fun getSourceAttributeByDataSource():
                ImmutableMap<DataSource<*>, SourceAttribute> {
                return sourceAttributesByDataSource
            }
        }

        private data class DefaultSourceIndexSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.SourceIndexSpec {
            override fun <SI : SourceIndex> forSourceAttribute(
                sourceAttribute: SourceAttribute
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    sourceIndex =
                        Try.attemptNullable(
                            {
                                @Suppress("UNCHECKED_CAST") //
                                sourceAttribute as? SI
                            },
                            {
                                SchemaException(
                                    SchemaErrorResponse.UNEXPECTED_ERROR,
                                    """not an instance of source 
                                        |attribute belonging to datasource 
                                        |source index type""".flattenIntoOneLine()
                                )
                            }
                        )
                )
            }

            override fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    sourceIndex =
                        Try.attemptNullable(
                            {
                                @Suppress("UNCHECKED_CAST") //
                                sourceContainerType as? SI
                            },
                            {
                                SchemaException(
                                    SchemaErrorResponse.UNEXPECTED_ERROR,
                                    """not an instance of source container
                                        |type belonging to datasource 
                                        |source index type""".flattenIntoOneLine()
                                )
                            }
                        )
                )
            }

            override fun fromExistingVertex(
                existingSchematicVertex: SchematicVertex
            ): SchematicVertexFactory.ExistingSchematicVertexSpec {
                return DefaultExistingSchematicVertexSpec(
                    schematicPath = schematicPath,
                    existingSchematicVertex = existingSchematicVertex
                )
            }
        }

        private data class DefaultExistingSchematicVertexSpec(
            val schematicPath: SchematicPath,
            val existingSchematicVertex: SchematicVertex
        ) : SchematicVertexFactory.ExistingSchematicVertexSpec {
            override fun <SI : SourceIndex> forSourceAttribute(
                sourceAttribute: SourceAttribute
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    sourceIndex =
                        Try.attemptNullable(
                            {
                                @Suppress("UNCHECKED_CAST") //
                                sourceAttribute as? SI
                            },
                            {
                                SchemaException(
                                    SchemaErrorResponse.UNEXPECTED_ERROR,
                                    """not an instance of source 
                                        |attribute belonging to datasource 
                                        |source index type""".flattenIntoOneLine()
                                )
                            }
                        ),
                    existingSchematicVertex = existingSchematicVertex.some()
                )
            }

            override fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    sourceIndex =
                        Try.attemptNullable(
                            {
                                @Suppress("UNCHECKED_CAST") //
                                sourceContainerType as? SI
                            },
                            {
                                SchemaException(
                                    SchemaErrorResponse.UNEXPECTED_ERROR,
                                    """not an instance of source container
                                        |type belonging to datasource 
                                        |source index type""".flattenIntoOneLine()
                                )
                            }
                        ),
                    existingSchematicVertex = existingSchematicVertex.some()
                )
            }
        }

        private data class DefaultDataSourceSpec<SI : SourceIndex>(
            val schematicPath: SchematicPath,
            val sourceIndex: Try<SI>,
            val existingSchematicVertex: Option<SchematicVertex> = none()
        ) : SchematicVertexFactory.DataSourceSpec<SI> {
            override fun onDataSource(dataSource: DataSource<SI>): SchematicVertex {
                TODO("Not yet implemented")
            }
        }
    }

    override fun createVertexForPath(
        schematicPath: SchematicPath
    ): SchematicVertexFactory.SourceIndexSpec {
        return DefaultSourceIndexSpec(schematicPath = schematicPath)
    }
}
