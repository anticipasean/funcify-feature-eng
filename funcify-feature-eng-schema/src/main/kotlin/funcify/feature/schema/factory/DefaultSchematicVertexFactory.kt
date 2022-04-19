package funcify.feature.schema.factory

import funcify.feature.schema.CompositeAttribute
import funcify.feature.schema.CompositeContainerType
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.SchematicVertexFactory
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.graph.RootVertex
import funcify.feature.schema.path.SchematicPath

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

        private data class DefaultSourceIndexSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.SourceIndexSpec {
            override fun forSourceAttribute(sourceAttribute: SourceAttribute): CompositeAttribute {
                TODO("Not yet implemented")
            }

            override fun <A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): CompositeContainerType {
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
            override fun forSourceAttribute(sourceAttribute: SourceAttribute): CompositeAttribute {
                TODO("Not yet implemented")
            }

            override fun <A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): CompositeContainerType {
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
