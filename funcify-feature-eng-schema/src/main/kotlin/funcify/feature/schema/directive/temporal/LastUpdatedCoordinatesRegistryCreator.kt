package funcify.feature.schema.directive.temporal

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.directive.LastUpdatedDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.scalars.ExtendedScalars
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitor
import graphql.schema.GraphQLTypeVisitorStub
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

object LastUpdatedCoordinatesRegistryCreator {

    private const val METHOD_TAG: String = "create_last_updated_coordinates_registry_for"
    private val logger: Logger = loggerFor<LastUpdatedCoordinatesRegistryCreator>()

    fun createLastUpdatedCoordinatesRegistryFor(
        graphQLSchema: GraphQLSchema,
        path: GQLOperationPath,
        fieldCoordinates: FieldCoordinates
    ): Try<LastUpdatedCoordinatesRegistry> {
        logger.info("{}: [ path: {}, field_coordinates: {} ]", METHOD_TAG, path, fieldCoordinates)
        return graphQLSchema
            .getType(fieldCoordinates.typeName)
            .toOption()
            .filterIsInstance<GraphQLFieldsContainer>()
            .successIfDefined {
                ServiceError.of(
                    "%s not found for type_name in [ field_coordinates: %s ]",
                    GraphQLFieldsContainer::class.simpleName,
                    fieldCoordinates
                )
            }
            .flatMap { gfc: GraphQLFieldsContainer ->
                graphql.introspection.Introspection.getFieldDef(
                        graphQLSchema,
                        gfc,
                        fieldCoordinates.fieldName
                    )
                    .toOption()
                    .successIfDefined {
                        ServiceError.of(
                            "%s not found for field_coordinates [ %s ]",
                            GraphQLFieldDefinition::class.simpleName,
                            fieldCoordinates
                        )
                    }
            }
            .flatMap { gfd: GraphQLFieldDefinition ->
                gfd.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapAll)
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .successIfDefined {
                        ServiceError.of(
                            "%s does not have type that unwraps to %s",
                            GraphQLFieldDefinition::class.simpleName,
                            GraphQLFieldsContainer::class.simpleName
                        )
                    }
            }
            .flatMap { gfc: GraphQLFieldsContainer ->
                Traverser.depthFirst<GraphQLSchemaElement>(
                        nodeTraversalFunction(graphQLSchema),
                        path,
                        LastUpdatedCoordinatesContext(
                            lastUpdatedCoordinatesByPath = persistentMapOf()
                        )
                    )
                    .traverse(
                        gfc,
                        LastUpdatedCoordinatesTraverserVisitor(LastUpdatedCoordinatesVisitor())
                    )
                    .successIfNonNull {
                        ServiceError.of(
                            "%s not returned for traversal",
                            TraverserResult::class.simpleName
                        )
                    }
                    .map(TraverserResult::getAccumulatedResult)
                    .filterInstanceOf<LastUpdatedCoordinatesContext>()
            }
            .map(LastUpdatedCoordinatesContext::lastUpdatedCoordinatesByPath)
            .map(::DefaultLastUpdatedCoordinatesRegistry)
    }

    private fun nodeTraversalFunction(
        graphQLSchema: GraphQLSchema
    ): (GraphQLSchemaElement) -> List<GraphQLSchemaElement> {
        return { e: GraphQLSchemaElement ->
            when (e) {
                is GraphQLInterfaceType -> {
                    e.fieldDefinitions
                        .asSequence()
                        .plus(graphQLSchema.getImplementations(e))
                        .toList()
                }
                is GraphQLObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLFieldDefinition -> {
                    e.type
                        .toOption()
                        .mapNotNull(GraphQLTypeUtil::unwrapAll)
                        .filterIsInstance<GraphQLFieldsContainer>()
                        .fold(::emptyList, ::listOf)
                }
                else -> {
                    emptyList<GraphQLSchemaElement>()
                }
            }
        }
    }

    private data class LastUpdatedCoordinatesContext(
        val lastUpdatedCoordinatesByPath: PersistentMap<GQLOperationPath, FieldCoordinates>
    )

    private class LastUpdatedCoordinatesTraverserVisitor(
        private val graphQLTypeVisitor: GraphQLTypeVisitor
    ) : TraverserVisitor<GraphQLSchemaElement> {

        override fun enter(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return context.thisNode().accept(context, graphQLTypeVisitor)
        }

        override fun leave(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return TraversalControl.CONTINUE
        }

        override fun backRef(context: TraverserContext<GraphQLSchemaElement>): TraversalControl {
            return graphQLTypeVisitor.visitBackRef(context)
        }
    }

    private class LastUpdatedCoordinatesVisitor : GraphQLTypeVisitorStub() {
        companion object {
            private val logger: Logger = loggerFor<LastUpdatedCoordinatesVisitor>()
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_field_definition: [ node.name: {} ]", node.name)
            if (node.allAppliedDirectivesByName.containsKey(LastUpdatedDirective.name)) {
                addLastUpdatedAnnotatedElementToRegistry(node, context)
            } else {
                val p: GQLOperationPath = createPathFromContext(node, context)
                context.setVar(GQLOperationPath::class.java, p)
            }
            return TraversalControl.CONTINUE
        }

        private fun addLastUpdatedAnnotatedElementToRegistry(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ) {
            when {
                !node.type
                    .toOption()
                    .map(GraphQLTypeUtil::unwrapNonNull)
                    .filterIsInstance<GraphQLScalarType>()
                    .filter { gst: GraphQLScalarType ->
                        sequenceOf(ExtendedScalars.Date, ExtendedScalars.DateTime)
                            .map(GraphQLScalarType::getName)
                            .any(gst.name::equals)
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        """field_definition [ node.name: %s ] 
                            |annotated with schema directive [ %s ] 
                            |not of temporal type [ %s ]"""
                            .flatten(),
                        node.name,
                        LastUpdatedDirective.name,
                        GraphQLTypeUtil.simplePrint(node.type)
                    )
                }
                context.parentContext.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLInterfaceType>()
                    .filter { git: GraphQLInterfaceType ->
                        git.fieldDefinitions
                            .asSequence()
                            .filter { gfd: GraphQLFieldDefinition ->
                                gfd.hasAppliedDirective(LastUpdatedDirective.name)
                            }
                            .count() > 1
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        "more than one field on %s [ name: %s ] is annotated with @%s directive",
                        GraphQLInterfaceType::class.simpleName,
                        context.parentContext.parentNode
                            .toOption()
                            .filterIsInstance<GraphQLInterfaceType>()
                            .map(GraphQLInterfaceType::getName)
                            .getOrElse { "<NA>" },
                        LastUpdatedDirective.name
                    )
                }
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLObjectType>()
                    .filter { got: GraphQLObjectType ->
                        got.fieldDefinitions
                            .asSequence()
                            .filter { gfd: GraphQLFieldDefinition ->
                                gfd.hasAppliedDirective(LastUpdatedDirective.name)
                            }
                            .count() > 1
                    }
                    .isDefined() -> {
                    throw ServiceError.of(
                        "more than one field on %s [ name: %s ] is annotated with @%s directive",
                        GraphQLObjectType::class.simpleName,
                        context.parentNode
                            .toOption()
                            .filterIsInstance<GraphQLObjectType>()
                            .map(GraphQLObjectType::getName)
                            .getOrElse { "<NA>" },
                        LastUpdatedDirective.name
                    )
                }
                else -> {
                    val p: GQLOperationPath = createPathFromContext(node, context)
                    context.setVar(GQLOperationPath::class.java, p)
                    val c: LastUpdatedCoordinatesContext = context.getCurrentAccumulate()
                    val gfc: GraphQLFieldsContainer =
                        when {
                            context.parentContext.parentNode
                                .toOption()
                                .filterIsInstance<GraphQLInterfaceType>()
                                .filter { git: GraphQLInterfaceType ->
                                    git.getFieldDefinition(node.name) != null
                                }
                                .isDefined() -> {
                                context.parentContext.parentNode as GraphQLFieldsContainer
                            }
                            context.parentNode
                                .toOption()
                                .filterIsInstance<GraphQLObjectType>()
                                .filter { got: GraphQLObjectType ->
                                    got.getFieldDefinition(node.name) != null
                                }
                                .isDefined() -> {
                                context.parentNode as GraphQLFieldsContainer
                            }
                            else -> {
                                throw ServiceError.of(
                                    "unexpected traversal: parent is not %s containing field by name [ node: %s ]",
                                    GraphQLObjectType::class.qualifiedName,
                                    node.name
                                )
                            }
                        }
                    context.setAccumulate(
                        c.copy(
                            lastUpdatedCoordinatesByPath =
                                c.lastUpdatedCoordinatesByPath.put(
                                    p,
                                    FieldCoordinates.coordinates(gfc, node.name)
                                )
                        )
                    )
                }
            }
        }

        private fun createPathFromContext(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): GQLOperationPath {
            val p: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
            return when (
                val grandparent: GraphQLSchemaElement? = context.parentContext?.parentNode
            ) {
                is GraphQLInterfaceType -> {
                    when (val parent: GraphQLSchemaElement? = context.parentNode) {
                        is GraphQLObjectType -> {
                            if (grandparent.getFieldDefinition(node.name) != null) {
                                p.transform { appendField(node.name) }
                            } else {
                                p.transform { appendInlineFragment(parent.name, node.name) }
                            }
                        }
                        else -> {
                            throw ServiceError.of(
                                "unexpected traversal pattern: grandparent of %s is %s but parent is not %s",
                                GraphQLFieldDefinition::class.qualifiedName,
                                GraphQLInterfaceType::class.qualifiedName,
                                GraphQLObjectType::class.qualifiedName
                            )
                        }
                    }
                }
                else -> {
                    when (val parent: GraphQLSchemaElement? = context.parentNode) {
                        is GraphQLObjectType -> {
                            p.transform { appendField(node.name) }
                        }
                        else -> {
                            throw ServiceError.of(
                                "unexpected traversal pattern: parent of %s is not %s",
                                GraphQLFieldDefinition::class.qualifiedName,
                                GraphQLObjectType::class.qualifiedName
                            )
                        }
                    }
                }
            }
        }

        private fun extractParentPathContextVariableOrThrow(
            context: TraverserContext<GraphQLSchemaElement>
        ): GQLOperationPath {
            return Try.attemptNullable {
                    context.getVarFromParents<GQLOperationPath>(GQLOperationPath::class.java)
                }
                .flatMap(Try.Companion::fromOption)
                .orElseTry {
                    Try.attemptNullable { context.getSharedContextData<GQLOperationPath>() }
                        .flatMap(Try.Companion::fromOption)
                }
                .orElseThrow { _: Throwable ->
                    ServiceError.of("parent_path has not been set as variable in traverser_context")
                }
        }
    }
}
