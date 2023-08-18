package funcify.feature.file.metadata.filter

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.directive.TransformDirective
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.schema.sdl.TypeDefinitionRegistryFilter
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeUtil
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-18
 */
class TransformAnnotatedFeatureDefinitionsFilter : TypeDefinitionRegistryFilter {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
        private val logger: Logger = loggerFor<TransformAnnotatedFeatureDefinitionsFilter>()
        private data class TransformAnnotatedFeatureDefinitionFilterContext(
            val errors: PersistentList<ServiceError>
        )
        private class TransformAnnotatedFeatureDefinitionTraverserVisitor(
            private val nodeVisitor: NodeVisitor
        ) : TraverserVisitor<Node<*>> {

            override fun enter(context: TraverserContext<Node<*>>): TraversalControl {
                return context.thisNode().accept(context, nodeVisitor)
            }

            override fun leave(context: TraverserContext<Node<*>>): TraversalControl {
                return TraversalControl.CONTINUE
            }
        }
        private class TransformAnnotatedFeatureDefinitionVisitor() : NodeVisitorStub() {

            override fun visitInterfaceTypeDefinition(
                node: InterfaceTypeDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                val c: TransformAnnotatedFeatureDefinitionFilterContext =
                    context.getCurrentAccumulate()
                val message: String =
                    "interface_type_definitions not currently supported in the context of feature_definitions"
                context.setAccumulate(c.copy(errors = c.errors.add(ServiceError.of(message))))
                return TraversalControl.CONTINUE
            }

            override fun visitUnionTypeDefinition(
                node: UnionTypeDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                val c: TransformAnnotatedFeatureDefinitionFilterContext =
                    context.getCurrentAccumulate()
                val message: String =
                    "union_type_definitions not currently supported in the context of feature_definitions"
                context.setAccumulate(c.copy(errors = c.errors.add(ServiceError.of(message))))
                return TraversalControl.CONTINUE
            }

            override fun visitObjectTypeDefinition(
                node: ObjectTypeDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                return TraversalControl.CONTINUE
            }

            override fun visitFieldDefinition(
                node: FieldDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                return when {
                    node.inputValueDefinitions.isEmpty() -> {
                        TraversalControl.CONTINUE
                    }
                    node.hasDirective(TransformDirective.name) -> {
                        TraversalControl.CONTINUE
                    }
                    else -> {
                        val c: TransformAnnotatedFeatureDefinitionFilterContext =
                            context.getCurrentAccumulate()
                        val message: String =
                            """field_definition [ name: %s ] for [ type.name: %s ] 
                                |takes arguments but is not annotated with 
                                |a directive [ name: %s ] specifying its transformation"""
                                .format(
                                    node.name,
                                    context.parentNode
                                        .toOption()
                                        .filterIsInstance<ObjectTypeDefinition>()
                                        .map(ObjectTypeDefinition::getName)
                                        .getOrElse { "<NA>" },
                                    TransformDirective.name
                                )
                                .flatten()
                        context.setAccumulate(
                            c.copy(errors = c.errors.add(ServiceError.of(message)))
                        )
                        TraversalControl.CONTINUE
                    }
                }
            }
        }
    }

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        logger.debug(
            "filter: [ type_definition_registry.types.size: {} ]",
            typeDefinitionRegistry.types().size
        )
        return typeDefinitionRegistry
            .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
            .toOption()
            .successIfDefined {
                ServiceError.of(
                    "query object_type_definition not defined for [ type: %s ]",
                    FileRegistryFeatureCalculator::class.qualifiedName
                )
            }
            .flatMap { otd: ObjectTypeDefinition ->
                Traverser.breadthFirst(
                        nodeTraversalFunction(typeDefinitionRegistry),
                        null,
                        TransformAnnotatedFeatureDefinitionFilterContext(
                            errors = persistentListOf()
                        )
                    )
                    .traverse(
                        otd,
                        TransformAnnotatedFeatureDefinitionTraverserVisitor(
                            nodeVisitor = TransformAnnotatedFeatureDefinitionVisitor()
                        )
                    )
                    .toOption()
                    .mapNotNull(TraverserResult::getAccumulatedResult)
                    .filterIsInstance<TransformAnnotatedFeatureDefinitionFilterContext>()
                    .successIfDefined {
                        ServiceError.of(
                            "instance of [ type: %s ] not populated through traverser or visitor",
                            TransformAnnotatedFeatureDefinitionFilterContext::class.qualifiedName
                        )
                    }
            }
            .flatMap { c: TransformAnnotatedFeatureDefinitionFilterContext ->
                if (c.errors.isNotEmpty()) {
                    c.errors.fold(ServiceError.builder().build(), ServiceError::plus).failure()
                } else {
                    Try.success(typeDefinitionRegistry)
                }
            }
            .toResult()
    }

    private fun nodeTraversalFunction(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): (Node<*>) -> List<Node<*>> {
        return { n: Node<*> ->
            when (n) {
                is InterfaceTypeDefinition -> {
                    listOf(n)
                }
                is ObjectTypeDefinition -> {
                    sequenceOf(n).plus(n.fieldDefinitions).toList()
                }
                is UnionTypeDefinition -> {
                    listOf(n)
                }
                is FieldDefinition -> {
                    n.type
                        .toOption()
                        .map(TypeUtil::unwrapAll)
                        .filter(typeDefinitionRegistry::hasType)
                        .flatMap { tn: TypeName -> typeDefinitionRegistry.getType(tn).toOption() }
                        .filter { td: TypeDefinition<*> ->
                            td is ImplementingTypeDefinition<*> || td is UnionTypeDefinition
                        }
                        .fold(::emptyList, ::listOf)
                }
                else -> {
                    emptyList()
                }
            }
        }
    }
}
