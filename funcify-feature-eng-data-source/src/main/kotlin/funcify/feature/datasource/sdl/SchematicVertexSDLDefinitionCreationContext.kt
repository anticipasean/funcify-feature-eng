package funcify.feature.datasource.sdl

import arrow.core.Option
import arrow.core.firstOrNone
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.Builder
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.index.CompositeParameterAttribute
import funcify.feature.schema.index.CompositeParameterContainerType
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SchematicGraphVertexType
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import graphql.language.*
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Immutable context providing what GraphQL SDL definitions have already been defined for a given
 * vertex in the [MetamodelGraph], its parent context (if it has a parent vertex), and its composite
 * source index value. Callers may "update" this context through the [update] function parameter
 * (effectively creating a new context instance with whatever new definitions that have been added
 * or old definitions that have been removed)
 *
 * Typically, the initial context that is created will be for the root source index and any declared
 * strategy functions will be called during the processing of each vertex. The [Builder.nextVertex]
 * will be called by the factory creating the desired [GraphQLSchema] until all vertices have been
 * processed.
 * @author smccarron
 * @created 2022-06-24
 */
sealed interface SchematicVertexSDLDefinitionCreationContext<V : SchematicVertex> {

    val scalarTypeDefinitionsByName: ImmutableMap<String, ScalarTypeDefinition>

    val directiveDefinitionsByName: ImmutableMap<String, DirectiveDefinition>

    val implementingTypeDefinitionsBySchematicPath:
        ImmutableMap<SchematicPath, ImplementingTypeDefinition<*>>

    val fieldDefinitionsBySchematicPath: ImmutableMap<SchematicPath, FieldDefinition>

    val directivesBySchematicPath: ImmutableMap<SchematicPath, Directive>

    val inputObjectTypeDefinitionsBySchematicPath:
        ImmutableMap<SchematicPath, InputObjectTypeDefinition>

    val inputValueDefinitionsBySchematicPath: ImmutableMap<SchematicPath, InputValueDefinition>

    val sdlDefinitionsBySchematicPath: ImmutableMap<SchematicPath, ImmutableSet<Node<*>>>

    val sdlTypeDefinitionsByName: ImmutableMap<String, Type<*>>

    val metamodelGraph: MetamodelGraph

    val currentVertex: V

    val path: SchematicPath
        get() = currentVertex.path

    val parentPath: Option<SchematicPath>
        get() = currentVertex.path.getParentPath()

    val parentVertex: Option<SchematicVertex>
        get() = parentPath.flatMap { pp -> metamodelGraph.getVertex(pp) }

    val parentContext: Option<SchematicVertexSDLDefinitionCreationContext<*>>
        get() = parentVertex.map { pv -> update { nextVertex(pv) } }

    val currentSDLDefinitionsForSchematicPath: ImmutableSet<Node<*>>
        get() = sdlDefinitionsBySchematicPath[path] ?: persistentSetOf()

    val currentGraphVertexType: SchematicGraphVertexType

    fun <SV : SchematicVertex> update(
        updater: Builder<V>.() -> Builder<SV>
    ): SchematicVertexSDLDefinitionCreationContext<SV>

    /**
     * The main methods the strategies will manipulate in the given context are:
     * - [addSDLDefinitionForSchematicPath]
     * - [removeSDLDefinitionForSchematicPath]
     *
     * The builder should handle updating related context fields e.g. [sdlTypeDefinitionsByName],
     * [namedSDLDefinitionsByName]
     *
     * [nextVertex] will be called by the materialization module when aggregating the sdl
     * definitions into the materialization [graphql.schema.GraphQLSchema]
     */
    interface Builder<V : SchematicVertex> {

        fun addSDLDefinitionForSchematicPath(
            schematicPath: SchematicPath,
            sdlDefinition: Node<*>
        ): Builder<V>

        fun removeSDLDefinitionForSchematicPath(
            schematicPath: SchematicPath,
            sdlDefinition: Node<*>
        ): Builder<V>

        fun <SV : SchematicVertex> nextVertex(nextVertex: SV): Builder<SV>

        fun build(): SchematicVertexSDLDefinitionCreationContext<V>
    }

    interface SourceRootVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceRootVertex> {

        override val currentGraphVertexType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_ROOT_VERTEX

        val compositeSourceContainerType: CompositeSourceContainerType
            get() = currentVertex.compositeContainerType

        val existingObjectTypeDefinition: Option<ObjectTypeDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<ObjectTypeDefinition>()
                    .firstOrNone()

        val existingInterfaceTypeDefinition: Option<InterfaceTypeDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<InterfaceTypeDefinition>()
                    .firstOrNone()
    }

    interface SourceJunctionVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceJunctionVertex> {

        override val currentGraphVertexType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_JUNCTION_VERTEX

        val compositeSourceContainerType: CompositeSourceContainerType
            get() = currentVertex.compositeContainerType

        val compositeSourceAttribute: CompositeSourceAttribute
            get() = currentVertex.compositeAttribute

        val existingObjectTypeDefinition: Option<ObjectTypeDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<ObjectTypeDefinition>()
                    .firstOrNone()

        val existingInterfaceTypeDefinition: Option<InterfaceTypeDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<InterfaceTypeDefinition>()
                    .firstOrNone()

        val existingFieldDefinition: Option<FieldDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<FieldDefinition>()
                    .firstOrNone()
    }

    interface SourceLeafVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<SourceLeafVertex> {

        override val currentGraphVertexType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.SOURCE_LEAF_VERTEX

        val compositeSourceAttribute: CompositeSourceAttribute
            get() = currentVertex.compositeAttribute

        val existingFieldDefinition: Option<FieldDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<FieldDefinition>()
                    .firstOrNone()
    }

    interface ParameterJunctionVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<ParameterJunctionVertex> {

        override val currentGraphVertexType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.PARAMETER_JUNCTION_VERTEX

        val compositeParameterContainerType: CompositeParameterContainerType
            get() = currentVertex.compositeParameterContainerType

        val compositeParameterAttribute: CompositeParameterAttribute
            get() = currentVertex.compositeParameterAttribute

        val existingArgumentDefinition: Option<InputValueDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<InputValueDefinition>()
                    .firstOrNone()

        val existingDirectionDefinition: Option<DirectiveDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<DirectiveDefinition>()
                    .firstOrNone()

        val existingInputObjectTypeDefinition: Option<InputObjectTypeDefinition>
            get() {
                return currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<InputObjectTypeDefinition>()
                    .firstOrNone()
            }
    }

    interface ParameterLeafVertexSDLDefinitionCreationContext :
        SchematicVertexSDLDefinitionCreationContext<ParameterLeafVertex> {

        override val currentGraphVertexType: SchematicGraphVertexType
            get() = SchematicGraphVertexType.PARAMETER_LEAF_VERTEX

        val compositeParameterAttribute: CompositeParameterAttribute
            get() = currentVertex.compositeParameterAttribute

        val existingInputValueDefinition: Option<InputValueDefinition>
            get() =
                currentSDLDefinitionsForSchematicPath
                    .filterIsInstance<InputValueDefinition>()
                    .firstOrNone()
    }
}
