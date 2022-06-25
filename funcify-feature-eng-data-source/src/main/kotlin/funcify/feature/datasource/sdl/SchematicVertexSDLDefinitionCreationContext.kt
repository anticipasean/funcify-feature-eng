package funcify.feature.datasource.sdl

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeParameterAttribute
import funcify.feature.schema.index.CompositeParameterContainerType
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import graphql.language.NamedNode
import graphql.language.Node
import graphql.language.ScalarTypeDefinition
import graphql.language.Type
import graphql.language.TypeName
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
sealed interface SchematicVertexSDLDefinitionCreationContext<V : SchematicVertex> {

    val scalarTypeDefinitionsByName: ImmutableMap<String, ScalarTypeDefinition>

    val namedSDLDefinitionsByName: ImmutableMap<String, NamedNode<*>>

    val sdlDefinitionsBySchematicPath: ImmutableMap<SchematicPath, Node<*>>

    val sdlTypeDefinitionsByName: ImmutableMap<String, Type<*>>

    val metamodelGraph: MetamodelGraph

    val currentVertex: V

    val path: SchematicPath
        get() = currentVertex.path

    val parentPath: Option<SchematicPath>
        get() = currentVertex.path.getParentPath()

    val parentVertex: Option<SchematicVertex>
        get() = parentPath.flatMap { pp -> metamodelGraph.getVertex(pp) }

    val existingSDLDefinition: Option<Node<*>>
        get() = sdlDefinitionsBySchematicPath[path].toOption()

    fun <SV : SchematicVertex> update(
        updater: Builder<V>.() -> Builder<SV>
    ): SchematicVertexSDLDefinitionCreationContext<SV>

    interface Builder<V : SchematicVertex> {

        fun addSDLDefinitionForSchematicPath(
            schematicPath: SchematicPath,
            sdlDefinition: Node<*>
        ): Builder<V>

        fun addNamedNonScalarSDLType(namedNonScalarType: TypeName): Builder<V>

        fun <SV : SchematicVertex> nextVertex(nextVertex: SV): Builder<SV>

        fun build(): SchematicVertexSDLDefinitionCreationContext<V>
    }

    interface SourceRootVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceRootVertex> {

        val compositeSourceContainerType: CompositeSourceContainerType
            get() = currentVertex.compositeContainerType
    }

    interface SourceJunctionVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceJunctionVertex> {

        val compositeSourceContainerType: CompositeSourceContainerType
            get() = currentVertex.compositeContainerType

        val compositeSourceAttribute: CompositeSourceAttribute
            get() = currentVertex.compositeAttribute
    }

    interface SourceLeafVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceLeafVertex> {

        val compositeSourceAttribute: CompositeSourceAttribute
            get() = currentVertex.compositeAttribute
    }

    interface ParameterJunctionVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<ParameterJunctionVertex> {

        val compositeParameterContainerType: CompositeParameterContainerType
            get() = currentVertex.compositeParameterContainerType

        val compositeParameterAttribute: CompositeParameterAttribute
            get() = currentVertex.compositeParameterAttribute
    }

    interface ParameterLeafVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<ParameterLeafVertex> {

        val compositeParameterAttribute: CompositeParameterAttribute
            get() = currentVertex.compositeParameterAttribute
    }
}
