package funcify.feature.schema.directive.alias

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.directive.AliasDirective
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.util.Breadcrumb
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitorStub
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-17
 */
internal class AliasRegistryComposer {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
        private const val DATA_ELEMENT_OBJECT_TYPE_NAME: String = "DataElement"
        private const val DATA_ELEMENT_FIELD_NAME: String = "dataElement"
        private val logger: Logger = loggerFor<AliasRegistryComposer>()

        private class AliasDirectiveVisitor : NodeVisitorStub() {

            companion object {
                private val logger: Logger = loggerFor<AliasDirectiveVisitor>()
            }

            override fun visitFieldDefinition(
                node: FieldDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                return if (node.hasDirective(AliasDirective.name)) {
                    node.getDirectives(AliasDirective.name).fold(TraversalControl.CONTINUE) {
                        tc: TraversalControl,
                        d: Directive ->
                        visitDirective(d, context)
                    }
                } else {
                    TraversalControl.CONTINUE
                }
            }

            override fun visitDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                if (node.name == AliasDirective.name) {
                    logger.info(
                        "alias directive found: {}",
                        context.breadcrumbs.asSequence().joinToString(", ", "[ ", " ]")
                    )
                    val aliasRegistry: AttributeAliasRegistry =
                        context.getNewAccumulate<AttributeAliasRegistry>()
                    val sp: SchematicPath =
                        SchematicPath.of {
                            pathSegments(
                                context.breadcrumbs
                                    .asSequence()
                                    .mapNotNull { bc: Breadcrumb<Node<*>> -> bc.node }
                                    .filterIsInstance<FieldDefinition>()
                                    .map { fd: FieldDefinition -> fd.name }
                                    .toList()
                            )
                        }
                    logger.info("alias schematicPath: {}", sp)
                    val updatedRegistry: AttributeAliasRegistry =
                        aliasRegistry.registerSourceVertexPathWithAlias(
                            sp,
                            node.argumentsByName["name"]
                                .toOption()
                                .map(Argument::getValue)
                                .filterIsInstance<StringValue>()
                                .map(StringValue::getValue)
                                .getOrElse { "<NA>" }
                        )
                    context.setAccumulate(updatedRegistry)
                }
                return TraversalControl.CONTINUE
            }
        }
    }

    fun createAliasRegistry(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Option<AttributeAliasRegistry> {
        logger.info(
            """create_alias_registry: 
            |[ type_definition_registry.parse_order.in_order.size: {}, 
            |query_object_type_definition.field_definitions.name: {} ]"""
                .flatten(),
            typeDefinitionRegistry.parseOrder.inOrder.size,
            typeDefinitionRegistry
                .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                .toOption()
                .map(ObjectTypeDefinition::getFieldDefinitions)
                .map { fds: List<FieldDefinition> ->
                    fds.asSequence().map { fd: FieldDefinition -> fd.name }
                }
                .fold(::emptySequence, ::identity)
                .joinToString(", ", "[ ", " ]")
        )
        return typeDefinitionRegistry
            .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
            .toOption()
            .flatMap { otd: ObjectTypeDefinition ->
                Traverser.breadthFirst<Node<*>>(
                        nodeTraversalFunction(typeDefinitionRegistry),
                        null,
                        AttributeAliasRegistry.newRegistry()
                    )
                    .traverse(
                        otd,
                        object : TraverserVisitorStub<Node<*>>() {
                            override fun enter(
                                context: TraverserContext<Node<*>>
                            ): TraversalControl {
                                return context.thisNode().accept(context, AliasDirectiveVisitor())
                            }
                        }
                    )
                    .toOption()
                    .map { tr: TraverserResult -> tr.accumulatedResult }
                    .filterIsInstance<AttributeAliasRegistry>()
            }
    }

    private fun nodeTraversalFunction(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): (Node<*>) -> List<Node<*>> {
        return { n: Node<*> ->
            when (n) {
                is ObjectTypeDefinition -> {
                    if (n.name == QUERY_OBJECT_TYPE_NAME) {
                        n.fieldDefinitions
                            .filter { fd: FieldDefinition -> fd.name == DATA_ELEMENT_FIELD_NAME }
                            .toList()
                    } else {
                        n.fieldDefinitions
                    }
                }
                is FieldDefinition -> {
                    listOf(n.type)
                }
                is Type<*> -> {
                    n.toOption()
                        .recurse { t: Type<*> ->
                            when (t) {
                                is NonNullType -> t.type.left().some()
                                is ListType -> t.type.left().some()
                                is TypeName -> t.right().some()
                                else -> none()
                            }
                        }
                        .flatMap { tn: TypeName ->
                            typeDefinitionRegistry
                                .getType(tn, ImplementingTypeDefinition::class.java)
                                .toOption()
                        }
                        .map { itd: ImplementingTypeDefinition<*> -> listOf(itd) }
                        .getOrElse { emptyList() }
                }
                else -> {
                    emptyList<Node<*>>()
                }
            }
        }
    }
}
