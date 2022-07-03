package funcify.feature.datasource.sdl.impl

import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.Builder
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContextFactory
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.ImplementingTypeDefinition
import graphql.language.NamedNode
import graphql.language.Node
import graphql.language.ScalarTypeDefinition
import graphql.language.Type
import graphql.language.TypeName
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-06-24
 */
internal class DefaultSchematicVertexSDLDefinitionCreationContextFactory :
    SchematicVertexSDLDefinitionCreationContextFactory {

    companion object {

        private val logger: Logger =
            loggerFor<DefaultSchematicVertexSDLDefinitionCreationContextFactory>()

        internal class DefaultSchematicSDLDefinitionCreationContextBuilder<V : SchematicVertex>(
            private var scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            private var namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            private var sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            private var sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            private var metamodelGraph: MetamodelGraph,
            private var currentVertex: V
        ) : Builder<V> {

            override fun addSDLDefinitionForSchematicPath(
                schematicPath: SchematicPath,
                sdlDefinition: Node<*>,
            ): Builder<V> {
                logger.debug(
                    """add_sdl_definition_for_schematic_path: 
                       |[ path: ${schematicPath}, 
                       |sdl_definition.type: ${sdlDefinition::class.simpleName} 
                       |]""".flattenIntoOneLine()
                )
                when (sdlDefinition) {
                    is ImplementingTypeDefinition<*> -> {
                        if (sdlDefinition.name !in sdlTypeDefinitionsByName) {
                            sdlTypeDefinitionsByName =
                                sdlTypeDefinitionsByName.put(
                                    sdlDefinition.name,
                                    TypeName.newTypeName(sdlDefinition.name).build()
                                )
                        }
                        namedSDLDefinitionsByName =
                            namedSDLDefinitionsByName.put(sdlDefinition.name, sdlDefinition)
                        sdlDefinitionsBySchematicPath =
                            sdlDefinitionsBySchematicPath.put(
                                schematicPath,
                                sdlDefinitionsBySchematicPath
                                    .getOrDefault(schematicPath, persistentSetOf())
                                    .add(sdlDefinition)
                            )
                    }
                    is NamedNode<*> -> {
                        namedSDLDefinitionsByName =
                            namedSDLDefinitionsByName.put(sdlDefinition.name, sdlDefinition)
                        sdlDefinitionsBySchematicPath =
                            sdlDefinitionsBySchematicPath.put(
                                schematicPath,
                                sdlDefinitionsBySchematicPath
                                    .getOrDefault(schematicPath, persistentSetOf())
                                    .add(sdlDefinition)
                            )
                    }
                    else -> {
                        sdlDefinitionsBySchematicPath =
                            sdlDefinitionsBySchematicPath.put(
                                schematicPath,
                                sdlDefinitionsBySchematicPath
                                    .getOrDefault(schematicPath, persistentSetOf())
                                    .add(sdlDefinition)
                            )
                    }
                }
                return this
            }

            override fun removeSDLDefinitionForSchematicPath(
                schematicPath: SchematicPath,
                sdlDefinition: Node<*>
            ): Builder<V> {
                return if (schematicPath in sdlDefinitionsBySchematicPath &&
                        sdlDefinition in
                            (sdlDefinitionsBySchematicPath[schematicPath] ?: persistentSetOf())
                ) {
                    sdlDefinitionsBySchematicPath =
                        sdlDefinitionsBySchematicPath.put(
                            schematicPath,
                            sdlDefinitionsBySchematicPath[schematicPath]!!.remove(sdlDefinition)
                        )
                    sdlTypeDefinitionsByName =
                        if (sdlDefinition is ImplementingTypeDefinition<*> &&
                                sdlDefinition.name in sdlTypeDefinitionsByName
                        ) {
                            sdlTypeDefinitionsByName.remove(sdlDefinition.name)
                        } else {
                            sdlTypeDefinitionsByName
                        }
                    namedSDLDefinitionsByName =
                        if (sdlDefinition is NamedNode &&
                                sdlDefinition.name in namedSDLDefinitionsByName
                        ) {
                            namedSDLDefinitionsByName.remove(sdlDefinition.name)
                        } else {
                            namedSDLDefinitionsByName
                        }
                    DefaultSchematicSDLDefinitionCreationContextBuilder<V>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                } else {
                    this
                }
            }

            override fun <SV : SchematicVertex> nextVertex(nextVertex: SV): Builder<SV> {
                return DefaultSchematicSDLDefinitionCreationContextBuilder<SV>(
                    scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                    namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                    sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                    sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                    metamodelGraph = metamodelGraph,
                    currentVertex = nextVertex
                )
            }

            override fun build(): SchematicVertexSDLDefinitionCreationContext<V> {
                @Suppress("UNCHECKED_CAST") //
                return when (val nextVertex: V = currentVertex) {
                    is SourceRootVertex -> {
                        DefaultSourceRootVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceJunctionVertex -> {
                        DefaultSourceJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceLeafVertex -> {
                        DefaultSourceLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterJunctionVertex -> {
                        DefaultParameterJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterLeafVertex -> {
                        DefaultParameterLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    else -> {
                        val expectedGraphVertexTypeNamesSet: String =
                            sequenceOf(
                                    SourceRootVertex::class,
                                    SourceJunctionVertex::class,
                                    SourceLeafVertex::class,
                                    ParameterJunctionVertex::class,
                                    ParameterLeafVertex::class
                                )
                                .map { kcls -> kcls.simpleName }
                                .joinToString(", ", "{ ", " }")
                        val message =
                            """current/next_vertex not instance of 
                               |graph vertex type: [ expected: 
                               |one of ${expectedGraphVertexTypeNamesSet}, 
                               |actual: ${currentVertex::class.qualifiedName} ]
                               |""".flattenIntoOneLine()
                        logger.error("build: [ status: failed ] [ message: {} ]", message)
                        throw SchemaException(SchemaErrorResponse.INVALID_INPUT, message)
                    }
                } as
                    SchematicVertexSDLDefinitionCreationContext<V>
            }
        }

        internal data class DefaultSourceRootVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceRootVertex
        ) : SourceRootVertexSDLDefinitionCreationContext {

            override fun <SV : SchematicVertex> update(
                updater: Builder<SourceRootVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder: DefaultSchematicSDLDefinitionCreationContextBuilder<SourceRootVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceRootVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }

        internal data class DefaultSourceJunctionVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceJunctionVertex
        ) : SourceJunctionVertexSDLDefinitionCreationContext {
            override fun <SV : SchematicVertex> update(
                updater: Builder<SourceJunctionVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder:
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceJunctionVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceJunctionVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }

        internal data class DefaultSourceLeafVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceLeafVertex
        ) : SourceLeafVertexSDLDefinitionCreationContext {
            override fun <SV : SchematicVertex> update(
                updater: Builder<SourceLeafVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder: DefaultSchematicSDLDefinitionCreationContextBuilder<SourceLeafVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceLeafVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }

        internal data class DefaultParameterJunctionVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: ParameterJunctionVertex
        ) : ParameterJunctionVertexSDLDefinitionCreationContext {
            override fun <SV : SchematicVertex> update(
                updater: Builder<ParameterJunctionVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder:
                    DefaultSchematicSDLDefinitionCreationContextBuilder<ParameterJunctionVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<ParameterJunctionVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }

        internal data class DefaultParameterLeafVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val namedSDLDefinitionsByName: PersistentMap<String, NamedNode<*>> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: ParameterLeafVertex
        ) : ParameterLeafVertexSDLDefinitionCreationContext {

            override fun <SV : SchematicVertex> update(
                updater: Builder<ParameterLeafVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder:
                    DefaultSchematicSDLDefinitionCreationContextBuilder<ParameterLeafVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<ParameterLeafVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        namedSDLDefinitionsByName = namedSDLDefinitionsByName,
                        sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                        sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                        metamodelGraph = metamodelGraph,
                        currentVertex = currentVertex
                    )
                return updater.invoke(builder).build()
            }
        }
    }

    override fun createInitialContextForRootSchematicVertexSDLDefinition(
        metamodelGraph: MetamodelGraph,
        scalarTypeDefinitions: List<ScalarTypeDefinition>
    ): SchematicVertexSDLDefinitionCreationContext<SourceRootVertex> {
        logger.debug(
            """create_initial_context_for_root_schematic_vertex_sdl_definition: 
               |[ metamodel_graph.vertices_by_path.size: 
               |${metamodelGraph.verticesByPath.size} ]
               |""".flattenIntoOneLine()
        )
        return when (val rootVertex: SchematicVertex? =
                metamodelGraph.verticesByPath[SchematicPath.getRootPath()]
        ) {
            is SourceRootVertex -> {
                DefaultSourceRootVertexSDLDefinitionCreationContext(
                    metamodelGraph = metamodelGraph,
                    currentVertex = rootVertex,
                    scalarTypeDefinitionsByName =
                        scalarTypeDefinitions
                            .stream()
                            .map { std -> std.name to std }
                            .reducePairsToPersistentMap()
                )
            }
            else -> {
                val message = "root_vertex missing in metamodel_graph"
                logger.error(
                    """create_initial_context_for_root_schematic_vertex_sdl_definition: 
                    |[ status: failed ] 
                    |[ message: $message ]
                    |""".flattenIntoOneLine()
                )
                throw SchemaException(SchemaErrorResponse.SCHEMATIC_INTEGRITY_VIOLATION, message)
            }
        }
    }
}
