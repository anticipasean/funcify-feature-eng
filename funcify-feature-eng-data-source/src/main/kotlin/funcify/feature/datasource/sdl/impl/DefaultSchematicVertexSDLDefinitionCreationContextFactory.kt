package funcify.feature.datasource.sdl.impl

import arrow.core.firstOrNone
import arrow.core.toOption
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
import graphql.language.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
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
            private var directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            private var implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            private var fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            private var directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            private var inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            private var inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
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
                sdlDefinitionsBySchematicPath =
                    sdlDefinitionsBySchematicPath.put(
                        schematicPath,
                        addOrReplaceExistingNodeTypeIfPresent(
                            sdlDefinition,
                            sdlDefinitionsBySchematicPath.getOrDefault(
                                schematicPath,
                                persistentSetOf()
                            )
                        )
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
                        // Only add if an implementing_type_definition is not already associated
                        // with another path
                        // There can be only one implementing_type_definition for a given type_name
                        if (
                            implementingTypeDefinitionsBySchematicPath.asSequence().none {
                                (path, itd) ->
                                itd.name == sdlDefinition.name && path != schematicPath
                            }
                        ) {
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath.put(
                                    schematicPath,
                                    sdlDefinition
                                )
                        }
                    }
                    is FieldDefinition -> {
                        schematicPath
                            .getParentPath()
                            .flatMap { pp ->
                                implementingTypeDefinitionsBySchematicPath[pp].toOption()
                            }
                            .fold(
                                {},
                                {
                                    fieldDefinitionsBySchematicPath =
                                        fieldDefinitionsBySchematicPath.put(
                                            schematicPath,
                                            sdlDefinition
                                        )
                                    updateParentNodeAfterAddition(schematicPath, sdlDefinition)
                                }
                            )
                    }
                    is Directive -> {
                        directivesBySchematicPath =
                            directivesBySchematicPath.put(schematicPath, sdlDefinition)
                        updateParentNodeAfterAddition(schematicPath, sdlDefinition)
                    }
                    is InputObjectTypeDefinition -> {
                        if (sdlDefinition.name !in sdlTypeDefinitionsByName) {
                            sdlTypeDefinitionsByName =
                                sdlTypeDefinitionsByName.put(
                                    sdlDefinition.name,
                                    TypeName.newTypeName(sdlDefinition.name).build()
                                )
                        }
                        // Only add if there is input_object_type_definition not already associated
                        // with a path
                        // There can be only one input_object_type_definition for a given type_name
                        if (
                            inputObjectTypeDefinitionsBySchematicPath.none { (path, iotd) ->
                                iotd.name == sdlDefinition.name && path != schematicPath
                            }
                        ) {
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath.put(
                                    schematicPath,
                                    sdlDefinition
                                )
                        }
                    }
                    is InputValueDefinition -> {
                        schematicPath
                            .getParentPath()
                            .flatMap { pp ->
                                inputObjectTypeDefinitionsBySchematicPath[pp].toOption()
                            }
                            .fold(
                                {},
                                {
                                    inputValueDefinitionsBySchematicPath =
                                        inputValueDefinitionsBySchematicPath.put(
                                            schematicPath,
                                            sdlDefinition
                                        )
                                    updateParentNodeAfterAddition(schematicPath, sdlDefinition)
                                }
                            )
                    }
                }
                return this
            }

            private fun addOrReplaceExistingNodeTypeIfPresent(
                newNode: Node<*>,
                nodeSet: PersistentSet<Node<*>>
            ): PersistentSet<Node<*>> {
                val addedFlagHolder: BooleanArray = booleanArrayOf(false)
                return nodeSet
                    .asSequence()
                    .map { n: Node<*> ->
                        when {
                            n is ImplementingTypeDefinition<*> &&
                                newNode is ImplementingTypeDefinition<*> -> {
                                addedFlagHolder[0] = true
                                newNode
                            }
                            n is FieldDefinition && newNode is FieldDefinition -> {
                                addedFlagHolder[0] = true
                                newNode
                            }
                            n is Directive && newNode is Directive -> {
                                addedFlagHolder[0] = true
                                newNode
                            }
                            n is InputObjectTypeDefinition &&
                                newNode is InputObjectTypeDefinition -> {
                                addedFlagHolder[0] = true
                                newNode
                            }
                            n is InputValueDefinition && newNode is InputValueDefinition -> {
                                addedFlagHolder[0] = true
                                newNode
                            }
                            else -> {
                                n
                            }
                        }
                    }
                    .toPersistentSet()
                    .let { set ->
                        if (!addedFlagHolder[0]) {
                            set.add(newNode)
                        } else {
                            set
                        }
                    }
            }

            private fun updateParentNodeAfterAddition(
                childPath: SchematicPath,
                childDefinition: Node<*>
            ) {
                when (
                    val definedParentNode: Node<*>? =
                        childPath
                            .getParentPath()
                            .flatMap { parentPath ->
                                sdlDefinitionsBySchematicPath[parentPath].toOption()
                            }
                            .flatMap { parentNodes ->
                                parentNodes
                                    .asIterable()
                                    .filter { node: Node<*> ->
                                        (node is ImplementingTypeDefinition<*> &&
                                            childDefinition is FieldDefinition) ||
                                            (node is FieldDefinition &&
                                                childDefinition is InputValueDefinition) ||
                                            childDefinition is Directive ||
                                            (node is InputObjectTypeDefinition &&
                                                childDefinition is InputValueDefinition)
                                    }
                                    .firstOrNone()
                            }
                            .orNull()
                ) {
                    is ObjectTypeDefinition -> {
                        if (childDefinition is FieldDefinition) {
                            /*
                             * entry for field_definition already exists -> replace it
                             */
                            if (
                                definedParentNode.fieldDefinitions.any { fd ->
                                    fd.name == childDefinition.name
                                }
                            ) {
                                /*
                                 * Recursive: Call own method on parent
                                 */
                                addSDLDefinitionForSchematicPath(
                                    // non-null assertion: parent_path cannot be null if parent
                                    // node was found
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.fieldDefinitions(
                                            definedParentNode.fieldDefinitions.map { fieldDef ->
                                                if (fieldDef.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    fieldDef
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                /*
                                 * field definition does not already exist -> add it
                                 */
                                addSDLDefinitionForSchematicPath(
                                    // non-null assertion: parent_path cannot be null if parent
                                    // node was found
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.fieldDefinition(childDefinition)
                                    }
                                )
                            }
                        } else if (childDefinition is Directive) {
                            if (definedParentNode.hasDirective(childDefinition.name)) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directives(
                                            definedParentNode.directives.map { dir ->
                                                if (dir.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    dir
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directive(childDefinition)
                                    }
                                )
                            }
                        }
                    }
                    is InterfaceTypeDefinition -> {
                        if (childDefinition is FieldDefinition) {
                            /*
                             * entry for field_definition already exists -> replace it
                             */
                            if (
                                definedParentNode.fieldDefinitions.any { fd ->
                                    fd.name == childDefinition.name
                                }
                            ) {
                                /*
                                 * Recursive: Call own method on parent
                                 */
                                addSDLDefinitionForSchematicPath(
                                    // non-null assertion: parent_path cannot be null if parent
                                    // node was found
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.definitions(
                                            definedParentNode.fieldDefinitions.map { fieldDef ->
                                                if (fieldDef.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    fieldDef
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                /*
                                 * field definition does not already exist -> add it
                                 */
                                addSDLDefinitionForSchematicPath(
                                    // non-null assertion: parent_path cannot be null if parent
                                    // node was found
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.definition(childDefinition)
                                    }
                                )
                            }
                        } else if (childDefinition is Directive) {
                            if (definedParentNode.hasDirective(childDefinition.name)) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directives(
                                            definedParentNode.directives.map { dir ->
                                                if (dir.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    dir
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directive(childDefinition)
                                    }
                                )
                            }
                        }
                    }
                    is FieldDefinition -> {
                        if (childDefinition is InputValueDefinition) {
                            if (
                                definedParentNode.inputValueDefinitions.any { ivd ->
                                    ivd.name == childDefinition.name
                                }
                            ) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.inputValueDefinitions(
                                            definedParentNode.inputValueDefinitions.map { ivd ->
                                                if (ivd.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    ivd
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.inputValueDefinition(childDefinition)
                                    }
                                )
                            }
                        } else if (childDefinition is Directive) {
                            if (definedParentNode.hasDirective(childDefinition.name)) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directives(
                                            definedParentNode.directives.map { dir ->
                                                if (dir.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    dir
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directive(childDefinition)
                                    }
                                )
                            }
                        }
                    }
                    is InputObjectTypeDefinition -> {
                        if (childDefinition is InputValueDefinition) {
                            if (
                                definedParentNode.inputValueDefinitions.any { ivd ->
                                    ivd.name == childDefinition.name
                                }
                            ) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.inputValueDefinitions(
                                            definedParentNode.inputValueDefinitions.map { ivd ->
                                                if (ivd.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    ivd
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.inputValueDefinition(childDefinition)
                                    }
                                )
                            }
                        } else if (childDefinition is Directive) {
                            if (definedParentNode.hasDirective(childDefinition.name)) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directives(
                                            definedParentNode.directives.map { dir ->
                                                if (dir.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    dir
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directive(childDefinition)
                                    }
                                )
                            }
                        }
                    }
                    is InputValueDefinition -> {
                        if (childDefinition is Directive) {
                            if (definedParentNode.hasDirective(childDefinition.name)) {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directives(
                                            definedParentNode.directives.map { dir ->
                                                if (dir.name == childDefinition.name) {
                                                    childDefinition
                                                } else {
                                                    dir
                                                }
                                            }
                                        )
                                    }
                                )
                            } else {
                                addSDLDefinitionForSchematicPath(
                                    childPath.getParentPath().orNull()!!,
                                    definedParentNode.transform { builder ->
                                        builder.directive(childDefinition)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            override fun removeSDLDefinitionForSchematicPath(
                schematicPath: SchematicPath,
                sdlDefinition: Node<*>
            ): Builder<V> {
                logger.debug(
                    """remove_sdl_definition_for_schematic_path: 
                       |[ path: ${schematicPath}, 
                       |sdl_definition.type: 
                       |${sdlDefinition::class.simpleName} 
                       |]""".flattenIntoOneLine()
                )
                if (
                    schematicPath in sdlDefinitionsBySchematicPath &&
                        sdlDefinition in
                            (sdlDefinitionsBySchematicPath[schematicPath] ?: persistentSetOf())
                ) {
                    sdlDefinitionsBySchematicPath =
                        sdlDefinitionsBySchematicPath.put(
                            schematicPath,
                            sdlDefinitionsBySchematicPath[schematicPath]!!.remove(sdlDefinition)
                        )
                    sdlTypeDefinitionsByName =
                        when {
                            sdlDefinition is ImplementingTypeDefinition<*> &&
                                sdlDefinition.name in sdlTypeDefinitionsByName -> {
                                sdlTypeDefinitionsByName.remove(sdlDefinition.name)
                            }
                            sdlDefinition is InputObjectTypeDefinition &&
                                sdlDefinition.name in sdlTypeDefinitionsByName -> {
                                sdlTypeDefinitionsByName.remove(sdlDefinition.name)
                            }
                            else -> {
                                sdlTypeDefinitionsByName
                            }
                        }
                    when (sdlDefinition) {
                        is ImplementingTypeDefinition<*> -> {
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath.remove(schematicPath)
                        }
                        is FieldDefinition -> {
                            fieldDefinitionsBySchematicPath =
                                fieldDefinitionsBySchematicPath.remove(schematicPath)
                        }
                        is DirectiveDefinition -> {
                            directivesBySchematicPath =
                                directivesBySchematicPath.remove(schematicPath)
                        }
                        is InputObjectTypeDefinition -> {
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath.remove(schematicPath)
                        }
                        is InputValueDefinition -> {
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath.remove(schematicPath)
                        }
                    }
                }
                if (
                    sdlDefinition !is ImplementingTypeDefinition<*> &&
                        sdlDefinition !is InputObjectTypeDefinition
                ) {
                    updateParentNodeAfterRemoval(schematicPath, sdlDefinition)
                }
                return this
            }

            private fun updateParentNodeAfterRemoval(
                childPath: SchematicPath,
                childDefinition: Node<*>
            ) {
                when (
                    val definedParentNode: Node<*>? =
                        childPath
                            .getParentPath()
                            .filter { parentPath -> parentPath in sdlDefinitionsBySchematicPath }
                            .flatMap { parentPath ->
                                sdlDefinitionsBySchematicPath[parentPath].toOption()
                            }
                            .flatMap { parentNodes ->
                                parentNodes
                                    .asIterable()
                                    .filter { node: Node<*> ->
                                        (childDefinition is FieldDefinition &&
                                            node is ImplementingTypeDefinition<*>) ||
                                            (childDefinition is Argument &&
                                                node is FieldDefinition) ||
                                            childDefinition is Directive ||
                                            childDefinition is InputValueDefinition
                                    }
                                    .firstOrNone()
                            }
                            .orNull()
                ) {
                    is ObjectTypeDefinition -> {
                        if (
                            childDefinition is FieldDefinition &&
                                childDefinition.name in definedParentNode.namedChildren.children
                        ) {
                            /*
                             * Recursive: Call own method on parent
                             */
                            addSDLDefinitionForSchematicPath(
                                // non-null assertion: parent_path cannot be null if parent
                                // node was found
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.fieldDefinitions(
                                        definedParentNode.fieldDefinitions.filter { fieldDef ->
                                            fieldDef.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        } else if (
                            childDefinition is Directive &&
                                definedParentNode.hasDirective(childDefinition.name)
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.directives(
                                        definedParentNode.directives.filter { dir ->
                                            dir.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        }
                    }
                    is InterfaceTypeDefinition -> {
                        if (
                            childDefinition is FieldDefinition &&
                                childDefinition.name in definedParentNode.namedChildren.children
                        ) {
                            /*
                             * Recursive: Call own method on parent
                             */
                            addSDLDefinitionForSchematicPath(
                                // non-null assertion: parent_path cannot be null if parent
                                // node was found
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.definitions(
                                        definedParentNode.fieldDefinitions.filter { fieldDef ->
                                            fieldDef.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        } else if (
                            childDefinition is Directive &&
                                definedParentNode.hasDirective(childDefinition.name)
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.directives(
                                        definedParentNode.directives.filter { dir ->
                                            dir.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        }
                    }
                    is FieldDefinition -> {
                        if (
                            childDefinition is InputValueDefinition &&
                                definedParentNode.inputValueDefinitions.any { ivd ->
                                    ivd.name == childDefinition.name
                                }
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.inputValueDefinitions(
                                        definedParentNode.inputValueDefinitions.filter { ivd ->
                                            ivd.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        } else if (
                            childDefinition is Directive &&
                                definedParentNode.hasDirective(childDefinition.name)
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.directives(
                                        definedParentNode.directives.filter { dir ->
                                            dir.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        }
                    }
                    is InputObjectTypeDefinition -> {
                        if (
                            childDefinition is InputValueDefinition &&
                                definedParentNode.inputValueDefinitions.any { ivd ->
                                    ivd.name == childDefinition.name
                                }
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.inputValueDefinitions(
                                        definedParentNode.inputValueDefinitions.filter { ivd ->
                                            ivd.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        } else if (
                            childDefinition is Directive &&
                                definedParentNode.hasDirective(childDefinition.name)
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.directives(
                                        definedParentNode.directives.filter { dir ->
                                            dir.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        }
                    }
                    is InputValueDefinition -> {
                        if (
                            childDefinition is Directive &&
                                definedParentNode.hasDirective(childDefinition.name)
                        ) {
                            addSDLDefinitionForSchematicPath(
                                childPath.getParentPath().orNull()!!,
                                definedParentNode.transform { builder ->
                                    builder.directives(
                                        definedParentNode.directives.filter { dir ->
                                            dir.name != childDefinition.name
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            override fun <SV : SchematicVertex> nextVertex(nextVertex: SV): Builder<SV> {
                return DefaultSchematicSDLDefinitionCreationContextBuilder<SV>(
                    scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                    implementingTypeDefinitionsBySchematicPath =
                        implementingTypeDefinitionsBySchematicPath,
                    fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                    directivesBySchematicPath = directivesBySchematicPath,
                    inputObjectTypeDefinitionsBySchematicPath =
                        inputObjectTypeDefinitionsBySchematicPath,
                    inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
                            directiveDefinitionsByName = directiveDefinitionsByName,
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath,
                            fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                            directivesBySchematicPath = directivesBySchematicPath,
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath,
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceJunctionVertex -> {
                        DefaultSourceJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            directiveDefinitionsByName = directiveDefinitionsByName,
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath,
                            fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                            directivesBySchematicPath = directivesBySchematicPath,
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath,
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is SourceLeafVertex -> {
                        DefaultSourceLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            directiveDefinitionsByName = directiveDefinitionsByName,
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath,
                            fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                            directivesBySchematicPath = directivesBySchematicPath,
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath,
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterJunctionVertex -> {
                        DefaultParameterJunctionVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            directiveDefinitionsByName = directiveDefinitionsByName,
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath,
                            fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                            directivesBySchematicPath = directivesBySchematicPath,
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath,
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath,
                            sdlDefinitionsBySchematicPath = sdlDefinitionsBySchematicPath,
                            sdlTypeDefinitionsByName = sdlTypeDefinitionsByName,
                            metamodelGraph = metamodelGraph,
                            currentVertex = nextVertex
                        )
                    }
                    is ParameterLeafVertex -> {
                        DefaultParameterLeafVertexSDLDefinitionCreationContext(
                            scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                            directiveDefinitionsByName = directiveDefinitionsByName,
                            implementingTypeDefinitionsBySchematicPath =
                                implementingTypeDefinitionsBySchematicPath,
                            fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                            directivesBySchematicPath = directivesBySchematicPath,
                            inputObjectTypeDefinitionsBySchematicPath =
                                inputObjectTypeDefinitionsBySchematicPath,
                            inputValueDefinitionsBySchematicPath =
                                inputValueDefinitionsBySchematicPath,
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
                }
                    as SchematicVertexSDLDefinitionCreationContext<V>
            }
        }

        internal data class DefaultSourceRootVertexSDLDefinitionCreationContext(
            override val scalarTypeDefinitionsByName: PersistentMap<String, ScalarTypeDefinition> =
                persistentMapOf(),
            override val directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            override val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            override val fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            override val directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            override val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            override val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
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
                        directiveDefinitionsByName = directiveDefinitionsByName,
                        implementingTypeDefinitionsBySchematicPath =
                            implementingTypeDefinitionsBySchematicPath,
                        fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                        directivesBySchematicPath = directivesBySchematicPath,
                        inputObjectTypeDefinitionsBySchematicPath =
                            inputObjectTypeDefinitionsBySchematicPath,
                        inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
            override val directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            override val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            override val fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            override val directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            override val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            override val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
                persistentMapOf(),
            override val sdlDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, PersistentSet<Node<*>>> =
                persistentMapOf(),
            override val sdlTypeDefinitionsByName: PersistentMap<String, Type<*>> =
                persistentMapOf(),
            override val metamodelGraph: MetamodelGraph,
            override val currentVertex: SourceJunctionVertex,
        ) : SourceJunctionVertexSDLDefinitionCreationContext {
            override fun <SV : SchematicVertex> update(
                updater: Builder<SourceJunctionVertex>.() -> Builder<SV>
            ): SchematicVertexSDLDefinitionCreationContext<SV> {
                val builder:
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceJunctionVertex> =
                    DefaultSchematicSDLDefinitionCreationContextBuilder<SourceJunctionVertex>(
                        scalarTypeDefinitionsByName = scalarTypeDefinitionsByName,
                        directiveDefinitionsByName = directiveDefinitionsByName,
                        implementingTypeDefinitionsBySchematicPath =
                            implementingTypeDefinitionsBySchematicPath,
                        fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                        directivesBySchematicPath = directivesBySchematicPath,
                        inputObjectTypeDefinitionsBySchematicPath =
                            inputObjectTypeDefinitionsBySchematicPath,
                        inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
            override val directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            override val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            override val fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            override val directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            override val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            override val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
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
                        directiveDefinitionsByName = directiveDefinitionsByName,
                        implementingTypeDefinitionsBySchematicPath =
                            implementingTypeDefinitionsBySchematicPath,
                        fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                        directivesBySchematicPath = directivesBySchematicPath,
                        inputObjectTypeDefinitionsBySchematicPath =
                            inputObjectTypeDefinitionsBySchematicPath,
                        inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
            override val directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            override val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            override val fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            override val directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            override val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            override val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
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
                        directiveDefinitionsByName = directiveDefinitionsByName,
                        implementingTypeDefinitionsBySchematicPath =
                            implementingTypeDefinitionsBySchematicPath,
                        fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                        directivesBySchematicPath = directivesBySchematicPath,
                        inputObjectTypeDefinitionsBySchematicPath =
                            inputObjectTypeDefinitionsBySchematicPath,
                        inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
            override val directiveDefinitionsByName: PersistentMap<String, DirectiveDefinition> =
                persistentMapOf(),
            override val implementingTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, ImplementingTypeDefinition<*>> =
                persistentMapOf(),
            override val fieldDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, FieldDefinition> =
                persistentMapOf(),
            override val directivesBySchematicPath: PersistentMap<SchematicPath, Directive> =
                persistentMapOf(),
            override val inputObjectTypeDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputObjectTypeDefinition> =
                persistentMapOf(),
            override val inputValueDefinitionsBySchematicPath:
                PersistentMap<SchematicPath, InputValueDefinition> =
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
                        directiveDefinitionsByName = directiveDefinitionsByName,
                        implementingTypeDefinitionsBySchematicPath =
                            implementingTypeDefinitionsBySchematicPath,
                        fieldDefinitionsBySchematicPath = fieldDefinitionsBySchematicPath,
                        directivesBySchematicPath = directivesBySchematicPath,
                        inputObjectTypeDefinitionsBySchematicPath =
                            inputObjectTypeDefinitionsBySchematicPath,
                        inputValueDefinitionsBySchematicPath = inputValueDefinitionsBySchematicPath,
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
        scalarTypeDefinitions: List<ScalarTypeDefinition>,
        directiveDefinitions: List<DirectiveDefinition>
    ): SchematicVertexSDLDefinitionCreationContext<SourceRootVertex> {
        logger.debug(
            """create_initial_context_for_root_schematic_vertex_sdl_definition: 
               |[ metamodel_graph.vertices_by_path.size: 
               |${metamodelGraph.pathBasedGraph.verticesByPath.size} ]
               |""".flattenIntoOneLine()
        )
        return when (
            val rootVertex: SchematicVertex? =
                metamodelGraph.pathBasedGraph.verticesByPath[SchematicPath.getRootPath()]
        ) {
            is SourceRootVertex -> {
                DefaultSourceRootVertexSDLDefinitionCreationContext(
                    metamodelGraph = metamodelGraph,
                    currentVertex = rootVertex,
                    scalarTypeDefinitionsByName =
                        scalarTypeDefinitions
                            .stream()
                            .map { std -> std.name to std }
                            .reducePairsToPersistentMap(),
                    directiveDefinitionsByName =
                        directiveDefinitions
                            .stream()
                            .map { dd -> dd.name to dd }
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
