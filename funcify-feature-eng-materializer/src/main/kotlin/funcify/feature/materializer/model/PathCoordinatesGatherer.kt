package funcify.feature.materializer.model

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.*
import graphql.util.Breadcrumb
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import java.util.*
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

internal object PathCoordinatesGatherer :
    (MaterializationMetamodelBuildContext) -> MaterializationMetamodelBuildContext {

    private const val METHOD_TAG: String = "path_coordinates_gatherer.invoke"
    private val logger: Logger = loggerFor<PathCoordinatesGatherer>()

    override fun invoke(
        materializationMetamodelBuildContext: MaterializationMetamodelBuildContext
    ): MaterializationMetamodelBuildContext {
        logger.debug("{}", METHOD_TAG)
        return Try.success(materializationMetamodelBuildContext)
            .flatMap(calculatePathsAndFieldCoordinatesWithinBuildContext())
            .map(updateBuildContextWithPathsGathered(materializationMetamodelBuildContext))
            .orElseThrow()
    }

    private fun calculatePathsAndFieldCoordinatesWithinBuildContext():
        (MaterializationMetamodelBuildContext) -> Try<PathGatheringContext> {
        return { mmf: MaterializationMetamodelBuildContext ->
            mmf.materializationGraphQLSchema.queryType
                .successIfNonNull {
                    ServiceError.of(
                        """materialization_metamodel_build_context.
                        |materialization_graphql_schema.
                        |query_type not available"""
                            .flatten()
                    )
                }
                .flatMap(calculatePathsAndFieldCoordinatesOffQueryObjectType(mmf))
        }
    }

    private fun calculatePathsAndFieldCoordinatesOffQueryObjectType(
        mmf: MaterializationMetamodelBuildContext
    ): (GraphQLObjectType) -> Try<PathGatheringContext> {
        return { got: GraphQLObjectType ->
            val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>> = LinkedList()
            Traverser.breadthFirst(
                    factGatheringTraversalFunction(mmf.materializationGraphQLSchema),
                    GQLOperationPath.getRootPath(),
                    DefaultPathGatheringContext(
                        childPathsByParentPath = persistentMapOf(),
                        querySchemaElementsByPath = persistentMapOf(),
                        fieldCoordinatesByPath = persistentMapOf(),
                        pathsByFieldCoordinates = persistentMapOf()
                    )
                )
                .traverse(
                    got,
                    SchemaElementTraverserVisitor(
                        graphQLTypeVisitor =
                            PathGatheringElementVisitor(backRefQueue = backRefQueue)
                    )
                )
                .toOption()
                .mapNotNull(TraverserResult::getAccumulatedResult)
                .filterIsInstance<PathGatheringContext>()
                .successIfDefined(pathGatheringContextNotDefinedErrorSupplier())
                // .map(gatherPathsAndCoordinatesUntilFirstBackRefSetExhausted(backRefQueue, mmf))
                .flatMap(ensureMaximumOperationDepthValid(mmf))
                .map(gatherPathsAndCoordinatesUntilMaximumOperationDepth(backRefQueue, mmf))
        }
    }

    private fun gatherPathsAndCoordinatesUntilFirstBackRefSetExhausted(
        backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>,
        mmf: MaterializationMetamodelBuildContext,
    ): (PathGatheringContext) -> PathGatheringContext {
        return { pgc: PathGatheringContext ->
            var c: PathGatheringContext = pgc
            while (backRefQueue.isNotEmpty()) {
                val (p: GQLOperationPath, gfc: GraphQLFieldsContainer) = backRefQueue.pollFirst()
                c =
                    Traverser.breadthFirst(
                            factGatheringTraversalFunction(mmf.materializationGraphQLSchema),
                            p,
                            c
                        )
                        .traverse(
                            gfc,
                            SchemaElementTraverserVisitor(
                                PathGatheringElementVisitor(backRefQueue = LinkedList())
                            )
                        )
                        .toOption()
                        .mapNotNull(TraverserResult::getAccumulatedResult)
                        .filterIsInstance<PathGatheringContext>()
                        .successIfDefined(pathGatheringContextNotDefinedErrorSupplier())
                        .orElseThrow()
            }
            c
        }
    }

    private fun gatherPathsAndCoordinatesUntilMaximumOperationDepth(
        backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>,
        mmf: MaterializationMetamodelBuildContext,
    ): (PathGatheringContext) -> PathGatheringContext {
        return { pgc: PathGatheringContext ->
            var c: PathGatheringContext = pgc
            while (
                backRefQueue.isNotEmpty() &&
                    backRefQueue.peekFirst().first.level() <
                        mmf.featureEngineeringModel.modelLimits.maximumOperationDepth
            ) {
                val (p: GQLOperationPath, gfc: GraphQLFieldsContainer) = backRefQueue.pollFirst()
                c =
                    Traverser.breadthFirst(
                            factGatheringTraversalFunction(mmf.materializationGraphQLSchema),
                            p,
                            c
                        )
                        .traverse(
                            gfc,
                            SchemaElementTraverserVisitor(
                                PathGatheringElementVisitor(backRefQueue = backRefQueue)
                            )
                        )
                        .toOption()
                        .mapNotNull(TraverserResult::getAccumulatedResult)
                        .filterIsInstance<PathGatheringContext>()
                        .successIfDefined(pathGatheringContextNotDefinedErrorSupplier())
                        .orElseThrow()
            }
            c
        }
    }

    private fun pathGatheringContextNotDefinedErrorSupplier(): () -> ServiceError {
        return { ServiceError.of("path_gathering_context not passed as traverser_result") }
    }

    private fun ensureMaximumOperationDepthValid(
        mmf: MaterializationMetamodelBuildContext
    ): (PathGatheringContext) -> Try<PathGatheringContext> {
        return { pgc: PathGatheringContext ->
            when {
                mmf.featureEngineeringModel.modelLimits.maximumOperationDepth > 0 -> {
                    Try.success(pgc)
                }
                else -> {
                    Try.failure(
                        ServiceError.of(
                            """materialization_metamodel_build_context.
                            |feature_engineering_model.
                            |model_limits.
                            |maximum_operation_depth is invalid: 
                            |[ maximum_operation_depth: %s ]"""
                                .flatten(),
                            mmf.featureEngineeringModel.modelLimits.maximumOperationDepth
                        )
                    )
                }
            }
        }
    }

    private fun updateBuildContextWithPathsGathered(
        mmf: MaterializationMetamodelBuildContext
    ): (PathGatheringContext) -> MaterializationMetamodelBuildContext {
        return { pgc: PathGatheringContext ->
            mmf.update {
                putAllParentChildPaths(pgc.childPathsByParentPath)
                putAllPathsForFieldCoordinates(pgc.fieldCoordinatesByPath)
                putAllFieldCoordinatesForPaths(pgc.pathsByFieldCoordinates)
                putAllGraphQLSchemaElementsForPaths(pgc.querySchemaElementsByPath)
            }
        }
    }

    private fun factGatheringTraversalFunction(
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
                is GraphQLTypeReference -> {
                    graphQLSchema
                        .getType(e.name)
                        .toOption()
                        .flatMap { gt: GraphQLType ->
                            when (gt) {
                                is GraphQLFieldsContainer -> gt.some()
                                is GraphQLInputFieldsContainer -> gt.some()
                                else -> none()
                            }
                        }
                        .fold(::emptyList, ::listOf)
                }
                is GraphQLFieldDefinition -> {
                    when (val gut: GraphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(e.type)) {
                        is GraphQLFieldsContainer -> {
                            e.arguments.asSequence().plus(gut).toList()
                        }
                        else -> {
                            e.arguments
                        }
                    }
                }
                is GraphQLArgument -> {
                    listOf(e.type)
                }
                is GraphQLInputObjectType -> {
                    e.fieldDefinitions
                }
                is GraphQLInputObjectField -> {
                    listOf(e.type)
                }
                else -> {
                    emptyList<GraphQLSchemaElement>()
                }
            }
        }
    }

    private interface PathGatheringContext {

        val childPathsByParentPath: ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>

        val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>

        val fieldCoordinatesByPath: ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>

        val pathsByFieldCoordinates: ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>

        fun update(transformer: Builder.() -> Builder): PathGatheringContext

        interface Builder {

            fun addChildPathForParentPath(
                parentPath: GQLOperationPath,
                childPath: GQLOperationPath
            ): Builder

            fun putGraphQLSchemaElementForPath(
                path: GQLOperationPath,
                element: GraphQLSchemaElement
            ): Builder

            fun putFieldCoordinatesForPath(
                path: GQLOperationPath,
                fieldCoordinates: FieldCoordinates
            ): Builder

            fun putPathForFieldCoordinates(
                fieldCoordinates: FieldCoordinates,
                path: GQLOperationPath
            ): Builder

            fun build(): PathGatheringContext
        }
    }

    private class DefaultPathGatheringContext(
        override val childPathsByParentPath:
            PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>>,
        override val querySchemaElementsByPath:
            PersistentMap<GQLOperationPath, GraphQLSchemaElement>,
        override val fieldCoordinatesByPath:
            PersistentMap<GQLOperationPath, PersistentSet<FieldCoordinates>>,
        override val pathsByFieldCoordinates:
            PersistentMap<FieldCoordinates, PersistentSet<GQLOperationPath>>
    ) : PathGatheringContext {
        companion object {
            private class DefaultBuilder(
                private val existingContext: DefaultPathGatheringContext,
                private val childPathsByParentPath:
                    PersistentMap.Builder<GQLOperationPath, PersistentSet<GQLOperationPath>> =
                    existingContext.childPathsByParentPath.builder(),
                private val querySchemaElementsByPath:
                    PersistentMap.Builder<GQLOperationPath, GraphQLSchemaElement> =
                    existingContext.querySchemaElementsByPath.builder(),
                private val fieldCoordinatesByPath:
                    PersistentMap.Builder<GQLOperationPath, PersistentSet<FieldCoordinates>> =
                    existingContext.fieldCoordinatesByPath.builder(),
                private val pathsByFieldCoordinates:
                    PersistentMap.Builder<FieldCoordinates, PersistentSet<GQLOperationPath>> =
                    existingContext.pathsByFieldCoordinates.builder(),
            ) : PathGatheringContext.Builder {

                override fun addChildPathForParentPath(
                    parentPath: GQLOperationPath,
                    childPath: GQLOperationPath
                ): PathGatheringContext.Builder =
                    this.apply {
                        this.childPathsByParentPath.put(
                            parentPath,
                            this.childPathsByParentPath
                                .getOrElse(parentPath, ::persistentSetOf)
                                .add(childPath)
                        )
                    }

                override fun putGraphQLSchemaElementForPath(
                    path: GQLOperationPath,
                    element: GraphQLSchemaElement,
                ): PathGatheringContext.Builder =
                    this.apply { this.querySchemaElementsByPath.put(path, element) }

                override fun putFieldCoordinatesForPath(
                    path: GQLOperationPath,
                    fieldCoordinates: FieldCoordinates
                ): PathGatheringContext.Builder =
                    this.apply {
                        this.fieldCoordinatesByPath.put(
                            path,
                            this.fieldCoordinatesByPath
                                .getOrElse(path, ::persistentSetOf)
                                .add(fieldCoordinates)
                        )
                    }

                override fun putPathForFieldCoordinates(
                    fieldCoordinates: FieldCoordinates,
                    path: GQLOperationPath
                ): PathGatheringContext.Builder =
                    this.apply {
                        this.pathsByFieldCoordinates.put(
                            fieldCoordinates,
                            this.pathsByFieldCoordinates
                                .getOrElse(fieldCoordinates, ::persistentSetOf)
                                .add(path)
                        )
                    }

                override fun build(): PathGatheringContext {
                    return DefaultPathGatheringContext(
                        childPathsByParentPath = childPathsByParentPath.build(),
                        querySchemaElementsByPath = querySchemaElementsByPath.build(),
                        fieldCoordinatesByPath = fieldCoordinatesByPath.build(),
                        pathsByFieldCoordinates = pathsByFieldCoordinates.build()
                    )
                }
            }
        }

        override fun update(
            transformer: PathGatheringContext.Builder.() -> PathGatheringContext.Builder
        ): PathGatheringContext {
            return transformer.invoke(DefaultBuilder(this)).build()
        }
    }

    private class SchemaElementTraverserVisitor(
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

    private class PathGatheringElementVisitor(
        private val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>>
    ) : GraphQLTypeVisitorStub() {

        companion object {
            private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
            private val logger: Logger = loggerFor<PathGatheringElementVisitor>()
        }

        override fun visitGraphQLInterfaceType(
            node: GraphQLInterfaceType,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_interface_type: [ node.name: {} ]", node.name)
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLObjectType(
            node: GraphQLObjectType,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_object_type: [ node.name: {} ]", node.name)
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLTypeReference(
            node: GraphQLTypeReference,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_type_reference: [ node.name: {} ]", node.name)
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLFieldDefinition(
            node: GraphQLFieldDefinition,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug(
                "visit_graphql_field_definition: [ node.name: {}, node.type: {} ]",
                node.name,
                GraphQLTypeUtil.simplePrint(node.type)
            )
            val parentPath: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
            when (val parentNode: GraphQLSchemaElement = context.parentNode) {
                is GraphQLInterfaceType -> {
                    val p: GQLOperationPath = parentPath.transform { appendField(node.name) }
                    updateContextWithPathAndFieldCoordinatesForGraphQLFieldDefinition(
                        context,
                        parentPath,
                        p,
                        node,
                        parentNode
                    )
                    context.setVar(GQLOperationPath::class.java, p)
                }
                is GraphQLObjectType -> {
                    when (
                        val grandparentNode: GraphQLFieldsContainer? =
                            context.parentContext?.parentNode as? GraphQLFieldsContainer
                    ) {
                        is GraphQLInterfaceType -> {
                            // Only add schema_element if field_definition unique to the parent
                            // node's object_type,
                            // not if already on interface_type
                            if (grandparentNode.getFieldDefinition(node.name) == null) {
                                val p: GQLOperationPath =
                                    parentPath.transform {
                                        appendInlineFragment(parentNode.name, node.name)
                                    }
                                updateContextWithPathAndFieldCoordinatesForGraphQLFieldDefinition(
                                    context,
                                    parentPath,
                                    p,
                                    node,
                                    parentNode
                                )
                                context.setVar(GQLOperationPath::class.java, p)
                            } else {
                                // Only add field_coordinates for object_type parent if grandparent
                                // is interface_type
                                val p: GQLOperationPath =
                                    parentPath.transform { appendField(node.name) }
                                updateContextWithOnlyFieldCoordinatesForGraphQLFieldDefinition(
                                    context,
                                    parentPath,
                                    p,
                                    node,
                                    parentNode
                                )
                                context.setVar(GQLOperationPath::class.java, p)
                            }
                        }
                        else -> {
                            val p: GQLOperationPath =
                                parentPath.transform { appendField(node.name) }
                            updateContextWithPathAndFieldCoordinatesForGraphQLFieldDefinition(
                                context,
                                parentPath,
                                p,
                                node,
                                parentNode
                            )
                            context.setVar(GQLOperationPath::class.java, p)
                        }
                    }
                }
            }

            return TraversalControl.CONTINUE
        }

        private fun updateContextWithOnlyFieldCoordinatesForGraphQLFieldDefinition(
            context: TraverserContext<GraphQLSchemaElement>,
            parentPath: GQLOperationPath,
            currentPath: GQLOperationPath,
            node: GraphQLFieldDefinition,
            parentNode: GraphQLObjectType,
        ) {
            val pgc: PathGatheringContext =
                context.getCurrentAccumulate<PathGatheringContext>().update {
                    addChildPathForParentPath(parentPath, currentPath)
                    putFieldCoordinatesForPath(
                        currentPath,
                        FieldCoordinates.coordinates(parentNode.name, node.name)
                    )
                    putPathForFieldCoordinates(
                        FieldCoordinates.coordinates(parentNode.name, node.name),
                        currentPath
                    )
                }
            context.setAccumulate(pgc)
        }

        private fun updateContextWithPathAndFieldCoordinatesForGraphQLFieldDefinition(
            context: TraverserContext<GraphQLSchemaElement>,
            parentPath: GQLOperationPath,
            currentPath: GQLOperationPath,
            node: GraphQLFieldDefinition,
            parentNode: GraphQLImplementingType,
        ) {
            val pgc: PathGatheringContext =
                context.getCurrentAccumulate<PathGatheringContext>().update {
                    addChildPathForParentPath(parentPath, currentPath)
                    putGraphQLSchemaElementForPath(currentPath, node)
                    putFieldCoordinatesForPath(
                        currentPath,
                        FieldCoordinates.coordinates(parentNode.name, node.name)
                    )
                    putPathForFieldCoordinates(
                        FieldCoordinates.coordinates(parentNode.name, node.name),
                        currentPath
                    )
                }
            context.setAccumulate(pgc)
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

        override fun visitGraphQLArgument(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_argument: [ node.name: {} ]", node.name)
            val parentPath: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
            val p: GQLOperationPath = parentPath.transform { argument(node.name) }
            val pgc: PathGatheringContext =
                context.getCurrentAccumulate<PathGatheringContext>().update {
                    addChildPathForParentPath(parentPath, p)
                    putGraphQLSchemaElementForPath(p, node)
                }
            context.setAccumulate(pgc)
            context.setVar(GQLOperationPath::class.java, p)
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLInputObjectType(
            node: GraphQLInputObjectType,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug("visit_graphql_input_object_type: [ node.name: {} ]", node.name)
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLInputObjectField(
            node: GraphQLInputObjectField,
            context: TraverserContext<GraphQLSchemaElement>,
        ): TraversalControl {
            // TODO: Different handling necessary here if input_object_field is nested field on an
            // input_object_type for a directive, not an argument
            logger.debug("visit_graphql_input_object_field: [ node.name: {} ]", node.name)
            val argumentName: Option<String> =
                context.breadcrumbs
                    .asSequence()
                    .takeWhile { bc: Breadcrumb<GraphQLSchemaElement> ->
                        bc.node !is GraphQLFieldDefinition
                    }
                    .firstOrNone { bc: Breadcrumb<GraphQLSchemaElement> ->
                        bc.node is GraphQLArgument
                    }
                    .map(Breadcrumb<GraphQLSchemaElement>::getNode)
                    .filterIsInstance<GraphQLArgument>()
                    .map(GraphQLArgument::getName)
            if (argumentName.isEmpty()) {
                throw IllegalStateException(
                    "argument_name could not be resolved for node: [ graphql_input_object_field.name: %s ]".format(
                        node.name
                    )
                )
            }
            val parentPath: GQLOperationPath = extractParentPathContextVariableOrThrow(context)
            val p: GQLOperationPath = parentPath.transform { appendArgumentPathSegment(node.name) }
            val pgc: PathGatheringContext =
                context.getCurrentAccumulate<PathGatheringContext>().update {
                    addChildPathForParentPath(parentPath, p)
                    putGraphQLSchemaElementForPath(p, node)
                }
            context.setAccumulate(pgc)
            context.setVar(GQLOperationPath::class.java, p)
            return TraversalControl.CONTINUE
        }

        override fun visitBackRef(
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            logger.debug(
                """visit_back_ref: [ context.parent_path: {}, 
                |context.parent_node[name,type]: {}, 
                |context.this_node[name,type]: {} ]"""
                    .flatten(),
                context
                    .getVarFromParents<GQLOperationPath>(GQLOperationPath::class.java)
                    .toDecodedURIString(),
                context.parentNode
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .zip(context.parentNode.run { this::class }.simpleName.toOption()) {
                        n: String,
                        t: String ->
                        "$n:$t"
                    }
                    .getOrElse { "<NA>" },
                context
                    .thisNode()
                    .toOption()
                    .filterIsInstance<GraphQLNamedSchemaElement>()
                    .map(GraphQLNamedSchemaElement::getName)
                    .zip(context.thisNode().run { this::class }.simpleName.toOption()) {
                        n: String,
                        t: String ->
                        "$n:$t"
                    }
                    .orElse {
                        context
                            .thisNode()
                            .run { this::class }
                            .qualifiedName
                            .toOption()
                            .map { t: String -> ",$t" }
                    }
                    .getOrElse { "<NA>" }
            )
            context
                .getVarFromParents<GQLOperationPath>(GQLOperationPath::class.java)
                .toOption()
                .zip(
                    context.parentNode.toOption().filterIsInstance<GraphQLFieldDefinition>(),
                    context.thisNode().toOption().filterIsInstance<GraphQLFieldsContainer>(),
                    ::Triple
                )
                .tap { t: Triple<GQLOperationPath, GraphQLFieldDefinition, GraphQLFieldsContainer>
                    ->
                    backRefQueue.offerLast(t.first to t.third)
                }
            return TraversalControl.CONTINUE
        }
    }
}
