package funcify.feature.schema.directive.alias

import arrow.core.*
import funcify.feature.directive.AliasDirective
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
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
internal class AttributeAliasRegistryComposer {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
        private const val DATA_ELEMENT_OBJECT_TYPE_NAME: String = "DataElement"
        private const val DATA_ELEMENT_FIELD_NAME: String = "dataElement"
        private val logger: Logger = loggerFor<AttributeAliasRegistryComposer>()

        private class GraphQLNodeTraversalVisitor(private val nodeVisitor: NodeVisitor) :
            TraverserVisitorStub<Node<*>>() {

            override fun enter(context: TraverserContext<Node<*>>): TraversalControl {
                return context.thisNode().accept(context, nodeVisitor)
            }
        }

        private class AliasDirectiveVisitor : NodeVisitorStub() {

            companion object {
                private val logger: Logger = loggerFor<AliasDirectiveVisitor>()
            }

            override fun visitDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug(
                    "visit_directive: [ node.name: {}, context.parent_node.type: {} ]",
                    node.name,
                    context.parentNode::class.simpleName
                )
                if (node.name == AliasDirective.name) {
                    addAliasToRegistry(context, node)
                }
                return TraversalControl.CONTINUE
            }

            private fun addAliasToRegistry(context: TraverserContext<Node<*>>, node: Directive) {
                // logger.info(
                //    "alias directive found: {}",
                //    context.breadcrumbs.asSequence().withIndex().joinToString(",\n", "[ ", " ]") {
                //        (idx: Int, bc: Breadcrumb<Node<*>>) ->
                //        "[$idx][ type: ${bc.node::class.simpleName}, name: %s ]".format(
                //            bc.node
                //                .toOption()
                //                .filterIsInstance<NamedNode<*>>()
                //                .map(NamedNode<*>::getName)
                //                .getOrElse { "<NA>" }
                //        )
                //    }
                // )
                val aliasRegistry: AttributeAliasRegistry =
                    context.getNewAccumulate<AttributeAliasRegistry>()
                val sp: GQLOperationPath =
                    when (val parentNode: Node<*> = context.parentNode) {
                        is FieldDefinition -> {
                            GQLOperationPath.of {
                                fields(
                                    context.breadcrumbs
                                        .asReversed()
                                        .asSequence()
                                        .mapNotNull(Breadcrumb<Node<*>>::getNode)
                                        .filterIsInstance<FieldDefinition>()
                                        .map(FieldDefinition::getName)
                                        .toList()
                                )
                            }
                        }
                        is InputValueDefinition -> {
                            GQLOperationPath.of {
                                fields(
                                    context.breadcrumbs
                                        .asReversed()
                                        .asSequence()
                                        .mapNotNull(Breadcrumb<Node<*>>::getNode)
                                        .filterIsInstance<FieldDefinition>()
                                        .map(FieldDefinition::getName)
                                        .toList()
                                )
                                argument(
                                    context.breadcrumbs
                                        .asSequence()
                                        .takeWhile { bc: Breadcrumb<Node<*>> ->
                                            bc.node !is FieldDefinition
                                        }
                                        .lastOrNull()
                                        .toOption()
                                        .mapNotNull(Breadcrumb<Node<*>>::getNode)
                                        .filterIsInstance<InputValueDefinition>()
                                        .map(InputValueDefinition::getName)
                                        .getOrElse { "<NA>" },
                                    when {
                                        context.breadcrumbs
                                            .asSequence()
                                            .take(2)
                                            .lastOrNull()
                                            .toOption()
                                            .mapNotNull(Breadcrumb<Node<*>>::getNode)
                                            .filterIsInstance<FieldDefinition>()
                                            .isDefined() -> {
                                            emptyList<String>()
                                        }
                                        else -> {
                                            context.breadcrumbs
                                                .asSequence()
                                                .takeWhile { bc: Breadcrumb<Node<*>> ->
                                                    bc.node !is FieldDefinition
                                                }
                                                .toMutableList()
                                                .apply { removeLastOrNull() }
                                                .asReversed()
                                                .asSequence()
                                                .mapNotNull(Breadcrumb<Node<*>>::getNode)
                                                .filterIsInstance<InputValueDefinition>()
                                                .map(InputValueDefinition::getName)
                                                .toList()
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            GQLOperationPath.getRootPath()
                        }
                    }
                if (logger.isDebugEnabled) {
                    logger.debug("add_alias_to_registry: [ created_path: {} ]", sp)
                }
                val updatedRegistry: AttributeAliasRegistry =
                    when (val parentNode: Node<*> = context.parentNode) {
                        is FieldDefinition -> {
                            sequenceOf(
                                    node.argumentsByName["name"]
                                        .toOption()
                                        .map(Argument::getValue)
                                        .filterIsInstance<StringValue>()
                                        .map(StringValue::getValue)
                                        .filter(String::isNotBlank)
                                )
                                .flatMapOptions()
                                .fold(aliasRegistry) { ar: AttributeAliasRegistry, n: String ->
                                    ar.registerSourceVertexPathWithAlias(sp, n)
                                }
                        }
                        is InputValueDefinition -> {
                            sequenceOf(
                                    node.argumentsByName["name"]
                                        .toOption()
                                        .map(Argument::getValue)
                                        .filterIsInstance<StringValue>()
                                        .map(StringValue::getValue)
                                        .filter(String::isNotBlank)
                                )
                                .flatMapOptions()
                                .fold(aliasRegistry) { ar: AttributeAliasRegistry, n: String ->
                                    ar.registerParameterVertexPathWithAlias(sp, n)
                                }
                        }
                        else -> {
                            aliasRegistry
                        }
                    }
                context.setAccumulate(updatedRegistry)
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
                .map(List<FieldDefinition>::asSequence)
                .fold(::emptySequence, ::identity)
                .map(FieldDefinition::getName)
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
                    .traverse(otd, GraphQLNodeTraversalVisitor(AliasDirectiveVisitor()))
                    .toOption()
                    .mapNotNull(TraverserResult::getAccumulatedResult)
                    .filterIsInstance<AttributeAliasRegistry>()
            }
    }

    private fun nodeTraversalFunction(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): (Node<*>) -> List<Node<*>> {
        return { n: Node<*> ->
            when (n) {
                is ObjectTypeDefinition -> {
                    n.fieldDefinitions
                }
                is FieldDefinition -> {
                    n.type
                        .some()
                        .recurse { t: Type<*> ->
                            when (t) {
                                is NonNullType -> t.type.left().some()
                                is ListType -> t.type.left().some()
                                is TypeName -> t.right().some()
                                else -> none()
                            }
                        }
                        .flatMap { typeName: TypeName ->
                            typeName
                                .toOption()
                                .filter(typeDefinitionRegistry::isObjectTypeOrInterface)
                                .flatMap { tn: TypeName ->
                                    typeDefinitionRegistry
                                        .getType(tn.name, ObjectTypeDefinition::class.java)
                                        .toOption()
                                        .orElse {
                                            typeDefinitionRegistry
                                                .getType(
                                                    tn.name,
                                                    InterfaceTypeDefinition::class.java
                                                )
                                                .toOption()
                                        }
                                }
                        }
                        .map { itd: ImplementingTypeDefinition<*> ->
                            buildList<Node<*>> {
                                addAll(n.directives)
                                addAll(n.inputValueDefinitions)
                                add(itd)
                            }
                        }
                        .getOrElse {
                            buildList<Node<*>> {
                                addAll(n.directives)
                                addAll(n.inputValueDefinitions)
                            }
                        }
                }
                is InputObjectTypeDefinition -> {
                    n.inputValueDefinitions
                }
                is InputValueDefinition -> {
                    n.type
                        .some()
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
                                .getType(tn.name, InputObjectTypeDefinition::class.java)
                                .toOption()
                        }
                        .map { iotd: InputObjectTypeDefinition ->
                            buildList<Node<*>> {
                                addAll(n.directives)
                                add(iotd)
                            }
                        }
                        .getOrElse { n.directives }
                }
                else -> {
                    emptyList<Node<*>>()
                }
            }
        }
    }
}