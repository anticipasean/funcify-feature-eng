package funcify.feature.materializer.type

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.directive.DiscriminatorDirective
import funcify.feature.directive.SubtypingDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.TypeResolutionEnvironment
import graphql.language.*
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType
import graphql.schema.TypeResolver
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitorStub
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-24
 */
internal class SubtypingDirectiveInterfaceSubtypeResolverFactory :
    MaterializationInterfaceSubtypeResolverFactory {

    companion object {
        private val logger: Logger = loggerFor<SubtypingDirectiveInterfaceSubtypeResolverFactory>()

        private class GraphQLNodeTraversalVisitor(private val nodeVisitor: NodeVisitor) :
            TraverserVisitorStub<Node<*>>() {

            override fun enter(context: TraverserContext<Node<*>>): TraversalControl {
                return context.thisNode().accept(context, nodeVisitor)
            }
        }

        private class SubtypingDirectiveVisitor : NodeVisitorStub() {

            companion object {
                private val logger: Logger = loggerFor<SubtypingDirectiveVisitor>()
            }

            override fun visitInterfaceTypeDefinition(
                node: InterfaceTypeDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_interface_type_definition: [ name: {} ]", node.name)
                return TraversalControl.CONTINUE
            }

            override fun visitObjectTypeDefinition(
                node: ObjectTypeDefinition,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_object_type_definition: [ name: {} ]", node.name)
                return TraversalControl.CONTINUE
            }

            override fun visitDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ): TraversalControl {
                logger.debug("visit_directive: [ name: {} ]", node.name)
                return when (node.name) {
                    SubtypingDirective.name -> {
                        createSubtypingStrategyForDirective(node, context)
                        TraversalControl.CONTINUE
                    }
                    DiscriminatorDirective.name -> {
                        createDiscriminatorValueEntryFromDirective(node, context)
                        TraversalControl.CONTINUE
                    }
                    else -> {
                        TraversalControl.CONTINUE
                    }
                }
            }

            private fun createSubtypingStrategyForDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ) {
                when (
                    val strategyName: String =
                        (node.getArgument("strategy")?.value as? EnumValue
                                ?: EnumValue("SUBTYPE_FIELD_NAME"))
                            .name
                ) {
                    "SUBTYPE_FIELD_NAME" -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<InterfaceTypeDefinition>()
                            .map { itd: InterfaceTypeDefinition ->
                                SubtypeFieldNameStrategy(
                                    interfaceTypeName = itd.name,
                                    objectSubtypeNames = persistentListOf(),
                                    discriminatorFieldNameByObjectSubtypeName = persistentMapOf()
                                )
                            }
                            .tap { sfns: SubtypeFieldNameStrategy -> context.setAccumulate(sfns) }
                    }
                    "SUBTYPE_FIELD_VALUE" -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<InterfaceTypeDefinition>()
                            .zip(
                                node
                                    .getArgument("discriminatorFieldName")
                                    .toOption()
                                    .map(Argument::getValue)
                                    .filterIsInstance<StringValue>()
                                    .mapNotNull(StringValue::getValue)
                                    .filter(String::isNotBlank),
                                node
                                    .getArgument("discriminatorFieldName")
                                    .toOption()
                                    .map(Argument::getValue)
                                    .filterIsInstance<StringValue>()
                                    .mapNotNull(StringValue::getValue)
                                    .filter(String::isNotBlank)
                                    .flatMap { dfn: String ->
                                        context.parentNode
                                            .toOption()
                                            .filterIsInstance<InterfaceTypeDefinition>()
                                            .map(InterfaceTypeDefinition::getFieldDefinitions)
                                            .map(List<FieldDefinition>::asSequence)
                                            .getOrElse { emptySequence() }
                                            .filter { fd: FieldDefinition -> fd.name == dfn }
                                            .firstOrNull()
                                            .toOption()
                                            .map(FieldDefinition::getType)
                                    },
                                ::Triple
                            )
                            .map { (itd: InterfaceTypeDefinition, dfn: String, dft: Type<*>) ->
                                SubtypeFieldValueStrategy(
                                    interfaceTypeName = itd.name,
                                    objectSubtypeNames = persistentListOf(),
                                    discriminatorFieldName = dfn,
                                    discriminatorFieldType = dft,
                                    discriminatorFieldValueByObjectSubtypeName = persistentMapOf()
                                )
                            }
                            .tap { sfvs: SubtypeFieldValueStrategy -> context.setAccumulate(sfvs) }
                    }
                    else -> {
                        val message: String =
                            """unsupported enum value for subtyping_strategy: 
                            |[ name: %s ]"""
                                .flatten()
                                .format(strategyName)
                        logger.error(
                            "create_subtyping_strategy_for_directive: [ status: failed ][ message: {} ]",
                            message
                        )
                        throw ServiceError.of(message)
                    }
                }
            }

            private fun createDiscriminatorValueEntryFromDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ) {
                when (
                    val strategy: SubtypingStrategy? =
                        context.getNewAccumulate<SubtypingStrategy?>()
                ) {
                    is SubtypeFieldNameStrategy -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<ObjectTypeDefinition>()
                            .zip(
                                node
                                    .getArgument("fieldName")
                                    .toOption()
                                    .map(Argument::getValue)
                                    .filterIsInstance<StringValue>()
                                    .map(StringValue::getValue)
                                    .filter(String::isNotBlank)
                                    .flatMap { fn: String ->
                                        context.parentNode
                                            .toOption()
                                            .filterIsInstance<ObjectTypeDefinition>()
                                            .map(ObjectTypeDefinition::getFieldDefinitions)
                                            .map(List<FieldDefinition>::asSequence)
                                            .getOrElse { emptySequence() }
                                            .filter { fd: FieldDefinition -> fd.name == fn }
                                            .firstOrNone()
                                    }
                            )
                            .map { (otd: ObjectTypeDefinition, fd: FieldDefinition) ->
                                strategy.copy(
                                    objectSubtypeNames = strategy.objectSubtypeNames.add(otd.name),
                                    discriminatorFieldNameByObjectSubtypeName =
                                        strategy.discriminatorFieldNameByObjectSubtypeName.put(
                                            otd.name,
                                            fd.name
                                        )
                                )
                            }
                            .tap { sfns: SubtypeFieldNameStrategy -> context.setAccumulate(sfns) }
                    }
                    is SubtypeFieldValueStrategy -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<ObjectTypeDefinition>()
                            .zip(
                                node
                                    .getArgument("fieldValue")
                                    .toOption()
                                    .map(Argument::getValue)
                                    .filterIsInstance<ObjectValue>()
                                    .flatMap(GraphQLValueToJsonNodeConverter)
                            )
                            .map { (otd: ObjectTypeDefinition, jv: JsonNode) ->
                                strategy.copy(
                                    objectSubtypeNames = strategy.objectSubtypeNames.add(otd.name),
                                    discriminatorFieldValueByObjectSubtypeName =
                                        strategy.discriminatorFieldValueByObjectSubtypeName.put(
                                            otd.name,
                                            jv
                                        )
                                )
                            }
                            .tap { sfvs: SubtypeFieldValueStrategy -> context.setAccumulate(sfvs) }
                    }
                    else -> {
                        // TODO: Create errors and pass them up to factory level
                    }
                }
            }
        }

        private sealed interface SubtypingStrategy {
            val interfaceTypeName: String
            val objectSubtypeNames: ImmutableList<String>
        }

        private data class SubtypeFieldNameStrategy(
            override val interfaceTypeName: String,
            override val objectSubtypeNames: PersistentList<String>,
            val discriminatorFieldNameByObjectSubtypeName: PersistentMap<String, String>
        ) : SubtypingStrategy

        private data class SubtypeFieldValueStrategy(
            override val interfaceTypeName: String,
            override val objectSubtypeNames: PersistentList<String>,
            val discriminatorFieldName: String,
            val discriminatorFieldType: Type<*>,
            val discriminatorFieldValueByObjectSubtypeName: PersistentMap<String, JsonNode>
        ) : SubtypingStrategy
    }

    override fun createTypeResolver(environment: InterfaceWiringEnvironment): TypeResolver {
        logger.debug(
            "create_type_resolver: [ environment.interface_type_definition.name: {} ]",
            environment.interfaceTypeDefinition.name
        )
        return Traverser.depthFirst<Node<*>>(
                nodeTraversalFunction(environment.registry),
                null,
                null
            )
            .traverse(
                environment.interfaceTypeDefinition,
                GraphQLNodeTraversalVisitor(SubtypingDirectiveVisitor()),
            )
            .toOption()
            .mapNotNull(TraverserResult::getAccumulatedResult)
            .filterIsInstance<SubtypingStrategy>()
            .map { ss: SubtypingStrategy ->
                when (ss) {
                    is SubtypeFieldNameStrategy -> {
                        createSubtypeFieldNameStrategyTypeResolver(ss)
                    }
                    is SubtypeFieldValueStrategy -> {
                        createSubtypeFieldValueStrategyTypeResolver(ss)
                    }
                }
            }
            .successIfDefined {
                ServiceError.of(
                    """unable to use directives to 
                    |create a type_resolver to resolve 
                    |interface_type_definition [ name: {} ] 
                    |into appropriate object subtypes"""
                        .flatten(),
                    environment.interfaceTypeDefinition.name
                )
            }
            .orElseThrow()
    }

    private fun nodeTraversalFunction(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): (Node<*>) -> List<Node<*>> {
        return { n: Node<*> ->
            when (n) {
                is InterfaceTypeDefinition -> {
                    sequenceOf(
                            n.getDirectives(SubtypingDirective.name),
                            typeDefinitionRegistry.getImplementationsOf(n)
                        )
                        .asSequence()
                        .flatten()
                        .toList()
                }
                is ObjectTypeDefinition -> {
                    n.getDirectives(DiscriminatorDirective.name)
                }
                else -> {
                    emptyList<Node<*>>()
                }
            }
        }
    }

    private fun createSubtypeFieldNameStrategyTypeResolver(
        subtypeFieldNameStrategy: SubtypeFieldNameStrategy
    ): TypeResolver {
        return TypeResolver { env: TypeResolutionEnvironment ->
            logger.info(
                "resolve: [ env.field.name: {}, env.fieldType: {} ]",
                env.field.name,
                env.fieldType
            )
            env.fieldType
                .toOption()
                .recurse { t: GraphQLType ->
                    when (t) {
                        is GraphQLNonNull -> t.wrappedType.left().some()
                        is GraphQLList -> t.wrappedType.left().some()
                        is GraphQLInterfaceType -> t.right().some()
                        else -> none()
                    }
                }
                .filter { git: GraphQLInterfaceType ->
                    git.name == subtypeFieldNameStrategy.interfaceTypeName
                }
                .and(
                    subtypeFieldNameStrategy.discriminatorFieldNameByObjectSubtypeName
                        .asSequence()
                        .firstOrNone { (otdn: String, fn: String) -> env.selectionSet.contains(fn) }
                )
                .flatMap { (otdn: String, fn: String) -> env.schema.getObjectType(otdn).toOption() }
                .successIfDefined {
                    ServiceError.of(
                        "unable to resolve object_type for environment: [ field.name: %s ]",
                        env.field.name
                    )
                }
                .orElseThrow()
        }
    }

    private fun createSubtypeFieldValueStrategyTypeResolver(
        subtypeFieldValueStrategy: SubtypeFieldValueStrategy
    ): TypeResolver {
        return TypeResolver { env: TypeResolutionEnvironment ->
            logger.info(
                "resolve: [ env.field.name: {}, env.fieldType: {} ]",
                env.field.name,
                env.fieldType
            )
            env.fieldType
                .toOption()
                .recurse { t: GraphQLType ->
                    when (t) {
                        is GraphQLNonNull -> t.wrappedType.left().some()
                        is GraphQLList -> t.wrappedType.left().some()
                        is GraphQLInterfaceType -> t.right().some()
                        else -> none()
                    }
                }
                .filter { git: GraphQLInterfaceType ->
                    git.name == subtypeFieldValueStrategy.interfaceTypeName
                }
                .and(
                    subtypeFieldValueStrategy.discriminatorFieldName
                        .some()
                        .flatMap { fn: String ->
                            env.getLocalContext<JsonNode?>()
                                .toOption()
                                .filterIsInstance<ObjectNode>()
                                .mapNotNull { on: ObjectNode -> on.get(fn) }
                        }
                        .flatMap { jv: JsonNode ->
                            subtypeFieldValueStrategy.discriminatorFieldValueByObjectSubtypeName
                                .asSequence()
                                .firstOrNone { (otdn: String, jn: JsonNode) -> jv == jn }
                        }
                )
                .flatMap { (otdn: String, jn: JsonNode) ->
                    env.schema.getObjectType(otdn).toOption()
                }
                .successIfDefined {
                    ServiceError.of(
                        "unable to resolve object_type for environment: [ field.name: %s ]",
                        env.field.name
                    )
                }
                .orElseThrow()
        }
    }
}
