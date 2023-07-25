package funcify.feature.materializer.type

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.directive.DiscriminatorDirective
import funcify.feature.directive.SubtypingDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.TypeResolutionEnvironment
import graphql.language.*
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
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
        private const val STRATEGY_ARGUMENT_NAME: String = "strategy"
        private const val DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME: String = "discriminatorFieldName"
        private const val FIELD_NAME_ARGUMENT_NAME: String = "fieldName"
        private const val FIELD_VALUE_ARGUMENT_NAME: String = "fieldValue"
        private const val FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE: String = "SUBTYPE_FIELD_NAME"
        private const val FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE: String = "SUBTYPE_FIELD_VALUE"

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
                        (node.getArgument(STRATEGY_ARGUMENT_NAME)?.value as? EnumValue
                                ?: EnumValue(FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE))
                            .name
                ) {
                    FIELD_NAME_SUBTYPING_STRATEGY_ENUM_VALUE -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<InterfaceTypeDefinition>()
                            .map { itd: InterfaceTypeDefinition ->
                                SubtypeFieldNameStrategySpec(
                                    interfaceTypeName = itd.name,
                                    objectSubtypeNames = persistentListOf(),
                                    objectSubtypeNameByDiscriminatorFieldName = persistentMapOf()
                                )
                            }
                            .tap { sfns: SubtypeFieldNameStrategySpec ->
                                context.setAccumulate(sfns)
                            }
                    }
                    FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<InterfaceTypeDefinition>()
                            .zip(
                                node
                                    .getArgument(DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME)
                                    .toOption()
                                    .map(Argument::getValue)
                                    .filterIsInstance<StringValue>()
                                    .mapNotNull(StringValue::getValue)
                                    .filter(String::isNotBlank),
                                node
                                    .getArgument(DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME)
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
                                SubtypeFieldValueStrategySpec(
                                    interfaceTypeName = itd.name,
                                    objectSubtypeNames = persistentListOf(),
                                    discriminatorFieldName = dfn,
                                    discriminatorFieldType = dft,
                                    objectSubtypeNameByDiscriminatorFieldValue = persistentMapOf()
                                )
                            }
                            .tap { sfvs: SubtypeFieldValueStrategySpec ->
                                context.setAccumulate(sfvs)
                            }
                            .orElse {
                                val interfaceTypeName: String =
                                    context.parentNode
                                        .toOption()
                                        .filterIsInstance<InterfaceTypeDefinition>()
                                        .map(InterfaceTypeDefinition::getName)
                                        .getOrElse { "<NA>" }
                                val message: String =
                                    if (
                                        node
                                            .getArgument(DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME)
                                            ?.value is NullValue
                                    ) {
                                        """argument "%s" not supplied for [ strategy: %s ] 
                                        |in @%s directive argument on 
                                        |[ interface.name: %s ]"""
                                            .flatten()
                                            .format(
                                                DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME,
                                                FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE,
                                                SubtypingDirective.name,
                                                interfaceTypeName
                                            )
                                    } else {
                                        """argument "%s" supplied for [ strategy: %s ] 
                                        |in @%s directive argument does not map 
                                        |to field_definition.name on 
                                        |[ interface.name: %s ]"""
                                            .flatten()
                                            .format(
                                                DISCRIMINATOR_FIELD_NAME_ARGUMENT_NAME,
                                                FIELD_VALUE_SUBTYPING_STRATEGY_ENUM_VALUE,
                                                SubtypingDirective.name,
                                                interfaceTypeName
                                            )
                                    }
                                SubtypingStrategyErrorsSpec(
                                        interfaceTypeName = interfaceTypeName,
                                        objectSubtypeNames = persistentListOf(),
                                        validationErrors =
                                            persistentListOf(ServiceError.of(message))
                                    )
                                    .toOption()
                                    .tap { sses: SubtypingStrategyErrorsSpec ->
                                        context.setAccumulate(sses)
                                    }
                            }
                    }
                    else -> {
                        val message: String =
                            """unsupported enum value for subtyping_strategy: 
                            |[ name: %s ]"""
                                .flatten()
                                .format(strategyName)
                        context.setAccumulate(
                            SubtypingStrategyErrorsSpec(
                                interfaceTypeName =
                                    context.parentNode
                                        .toOption()
                                        .filterIsInstance<InterfaceTypeDefinition>()
                                        .map(InterfaceTypeDefinition::getName)
                                        .getOrElse { "<NA>" },
                                objectSubtypeNames = persistentListOf(),
                                validationErrors = persistentListOf(ServiceError.of(message))
                            )
                        )
                    }
                }
            }

            private fun createDiscriminatorValueEntryFromDirective(
                node: Directive,
                context: TraverserContext<Node<*>>
            ) {
                when (
                    val strategy: SubtypingStrategySpec? =
                        context.getNewAccumulate<SubtypingStrategySpec?>()
                ) {
                    is SubtypeFieldNameStrategySpec -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<ObjectTypeDefinition>()
                            .zip(
                                node
                                    .getArgument(FIELD_NAME_ARGUMENT_NAME)
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
                                    objectSubtypeNameByDiscriminatorFieldName =
                                        strategy.objectSubtypeNameByDiscriminatorFieldName.put(
                                            fd.name,
                                            otd.name
                                        )
                                )
                            }
                            .tap { sfns: SubtypeFieldNameStrategySpec ->
                                context.setAccumulate(sfns)
                            }
                            .orElse {
                                val objectTypeName: String =
                                    context.parentNode
                                        .toOption()
                                        .filterIsInstance<ObjectTypeDefinition>()
                                        .map(ObjectTypeDefinition::getName)
                                        .getOrElse { "<NA>" }
                                val message: String =
                                    if (
                                        node.getArgument(FIELD_NAME_ARGUMENT_NAME)?.value
                                            is NullValue
                                    ) {
                                        """null value supplied for 
                                        |argument "%s" on @%s 
                                        |directive on [ object.name: %s ]"""
                                            .flatten()
                                            .format(
                                                FIELD_NAME_ARGUMENT_NAME,
                                                DiscriminatorDirective.name,
                                                objectTypeName
                                            )
                                    } else {
                                        """value supplied for 
                                        |argument "%s" on @%s 
                                        |directive does not map to a 
                                        |field_definition.name on [ object.name: %s ]"""
                                            .flatten()
                                            .format(
                                                FIELD_NAME_ARGUMENT_NAME,
                                                DiscriminatorDirective.name,
                                                objectTypeName
                                            )
                                    }
                                SubtypingStrategyErrorsSpec(
                                        interfaceTypeName = strategy.interfaceTypeName,
                                        objectSubtypeNames =
                                            strategy.objectSubtypeNames.add(objectTypeName),
                                        validationErrors =
                                            persistentListOf(ServiceError.of(message))
                                    )
                                    .toOption()
                                    .tap { sses: SubtypingStrategyErrorsSpec ->
                                        context.setAccumulate(sses)
                                    }
                            }
                    }
                    is SubtypeFieldValueStrategySpec -> {
                        context.parentNode
                            .toOption()
                            .filterIsInstance<ObjectTypeDefinition>()
                            .zip(
                                node
                                    .getArgument(FIELD_VALUE_ARGUMENT_NAME)
                                    .toOption()
                                    .map(Argument::getValue)
                                    .flatMap(GraphQLValueToJsonNodeConverter)
                                    .filterNot(JsonNode::isNull)
                            )
                            .map { (otd: ObjectTypeDefinition, jv: JsonNode) ->
                                strategy.copy(
                                    objectSubtypeNames = strategy.objectSubtypeNames.add(otd.name),
                                    objectSubtypeNameByDiscriminatorFieldValue =
                                        strategy.objectSubtypeNameByDiscriminatorFieldValue.put(
                                            jv,
                                            otd.name
                                        )
                                )
                            }
                            .tap { sfvs: SubtypeFieldValueStrategySpec ->
                                context.setAccumulate(sfvs)
                            }
                            .orElse {
                                val objectTypeName: String =
                                    context.parentNode
                                        .toOption()
                                        .filterIsInstance<ObjectTypeDefinition>()
                                        .map(ObjectTypeDefinition::getName)
                                        .getOrElse { "<NA>" }
                                val message: String =
                                    """null or missing value supplied for 
                                        |argument "%s" on @%s 
                                        |directive on [ object %s ]"""
                                        .flatten()
                                        .format(
                                            FIELD_VALUE_ARGUMENT_NAME,
                                            DiscriminatorDirective.name,
                                            objectTypeName
                                        )
                                SubtypingStrategyErrorsSpec(
                                        interfaceTypeName = strategy.interfaceTypeName,
                                        objectSubtypeNames =
                                            strategy.objectSubtypeNames.add(objectTypeName),
                                        validationErrors =
                                            persistentListOf(ServiceError.of(message))
                                    )
                                    .toOption()
                                    .tap { sses: SubtypingStrategyErrorsSpec ->
                                        context.setAccumulate(sses)
                                    }
                            }
                    }
                    else -> {}
                }
            }
        }

        private sealed interface SubtypingStrategySpec {
            val interfaceTypeName: String
            val objectSubtypeNames: ImmutableList<String>
        }

        private data class SubtypeFieldNameStrategySpec(
            override val interfaceTypeName: String,
            override val objectSubtypeNames: PersistentList<String>,
            val objectSubtypeNameByDiscriminatorFieldName: PersistentMap<String, String>
        ) : SubtypingStrategySpec

        private data class SubtypeFieldValueStrategySpec(
            override val interfaceTypeName: String,
            override val objectSubtypeNames: PersistentList<String>,
            val discriminatorFieldName: String,
            val discriminatorFieldType: Type<*>,
            val objectSubtypeNameByDiscriminatorFieldValue: PersistentMap<JsonNode, String>
        ) : SubtypingStrategySpec

        private data class SubtypingStrategyErrorsSpec(
            override val interfaceTypeName: String,
            override val objectSubtypeNames: PersistentList<String>,
            val validationErrors: PersistentList<ServiceError>
        ) : SubtypingStrategySpec
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
            .filterIsInstance<SubtypingStrategySpec>()
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
            .flatMap { ss: SubtypingStrategySpec ->
                when (ss) {
                    is SubtypeFieldNameStrategySpec -> {
                        Try.success(createSubtypeFieldNameStrategyTypeResolver(ss))
                    }
                    is SubtypeFieldValueStrategySpec -> {
                        Try.success(createSubtypeFieldValueStrategyTypeResolver(ss))
                    }
                    is SubtypingStrategyErrorsSpec -> {
                        ss.validationErrors
                            .fold(ServiceError.builder().build(), ServiceError::plus)
                            .also { se: ServiceError ->
                                logger.error(
                                    "create_type_resolver: [ status: failed ][ json: {} ]",
                                    se.toJsonNode()
                                )
                            }
                            .failure<TypeResolver>()
                    }
                }
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
        subtypeFieldNameStrategy: SubtypeFieldNameStrategySpec
    ): TypeResolver {
        return TypeResolver { env: TypeResolutionEnvironment ->
            logger.info(
                "resolve_object_type: [ env.field.name: {}, env.fieldType: {} ]",
                env.field.name,
                GraphQLTypeUtil.simplePrint(env.fieldType)
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
                    subtypeFieldNameStrategy.objectSubtypeNameByDiscriminatorFieldName
                        .asSequence()
                        .firstOrNone { (fn: String, otdn: String) -> env.selectionSet.contains(fn) }
                )
                .flatMap { (fn: String, otdn: String) -> env.schema.getObjectType(otdn).toOption() }
                .successIfDefined {
                    ServiceError.of(
                        "unable to resolve object_type for environment: [ field.name: %s, field.type: %s ]",
                        env.field.name,
                        GraphQLTypeUtil.simplePrint(env.fieldType)
                    )
                }
                .orElseThrow()
        }
    }

    private fun createSubtypeFieldValueStrategyTypeResolver(
        subtypeFieldValueStrategy: SubtypeFieldValueStrategySpec
    ): TypeResolver {
        return TypeResolver { env: TypeResolutionEnvironment ->
            logger.info(
                "resolve_object_type: [ env.field.name: {}, env.fieldType: {} ]",
                env.field.name,
                GraphQLTypeUtil.simplePrint(env.fieldType)
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
                            env.getObject<JsonNode?>()
                                .toOption()
                                .filterIsInstance<ObjectNode>()
                                .mapNotNull { on: ObjectNode -> on.get(fn) }
                                .orElse {
                                    env.getObject<Map<String, Any?>?>()
                                        .toOption()
                                        .filterIsInstance<Map<String, Any?>>()
                                        .mapNotNull { m: Map<String, Any?> -> m[fn] }
                                        .filterIsInstance<JsonNode>()
                                }
                        }
                        .flatMap { jv: JsonNode ->
                            subtypeFieldValueStrategy.objectSubtypeNameByDiscriminatorFieldValue
                                .asSequence()
                                .firstOrNone { (jn: JsonNode, otdn: String) -> jv == jn }
                        }
                )
                .flatMap { (jn: JsonNode, otdn: String) ->
                    env.schema.getObjectType(otdn).toOption()
                }
                .successIfDefined {
                    ServiceError.of(
                        "unable to resolve object_type for environment: [ field.name: %s, field.type: %s ]",
                        env.field.name,
                        GraphQLTypeUtil.simplePrint(env.fieldType)
                    )
                }
                .orElseThrow()
        }
    }
}
