package funcify.feature.schema.factory

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.CompositeAttribute
import funcify.feature.schema.CompositeContainerType
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.SchematicVertexFactory
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.graph.RootVertex
import funcify.feature.schema.path.SchematicPath
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

            fun <SI : SourceIndex, SA> put(
                dataSource: DataSource<SI>,
                sourceContainerType: SourceContainerType<SA>
            ): DefaultCompositeContainerType where SA : SourceAttribute {
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

            fun <SI : SourceIndex, SA> put(
                dataSource: DataSource<SI>,
                sourceAttribute: SourceAttribute
            ): DefaultCompositeAttribute where SA : SourceAttribute {
                return DefaultCompositeAttribute(
                    conventionalName = conventionalName,
                    sourceAttributesByDataSource =
                        sourceAttributesByDataSource.put(dataSource, sourceAttribute)
                )
            }
            override fun getSourceAttributeByDataSource():
                ImmutableMap<DataSource<*>, SourceAttribute> {
                TODO("Not yet implemented")
            }
        }

        private data class DefaultSourceIndexSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.SourceIndexSpec {
            override fun forSourceAttribute(sourceAttribute: SourceAttribute): SchematicVertex {
                TODO("Not yet implemented")
            }

            override fun <A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertex {
                TODO("Not yet implemented")
            }

            override fun fromExistingVertex(
                existingSchematicVertex: SchematicVertex
            ): SchematicVertexFactory.ExistingSchematicVertexSpec {
                TODO("Not yet implemented")
            }
        }

        private data class DefaultExistingSchematicVertexSpec(
            val schematicPath: SchematicPath,
            val existingSchematicVertex: SchematicVertex
        ) : SchematicVertexFactory.ExistingSchematicVertexSpec {
            override fun forSourceAttribute(sourceAttribute: SourceAttribute): SchematicVertex {
                TODO("Not yet implemented")
            }

            override fun <A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertex {
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
