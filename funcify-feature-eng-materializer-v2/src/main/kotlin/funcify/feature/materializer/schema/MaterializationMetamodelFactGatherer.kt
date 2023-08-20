package funcify.feature.materializer.schema

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.TypeDefinition
import graphql.schema.*
import graphql.util.Breadcrumb
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserResult
import graphql.util.TraverserVisitor
import java.util.*
import org.slf4j.Logger

internal object MaterializationMetamodelFactGatherer :
    (FeatureEngineeringModel, GraphQLSchema) -> MaterializationMetamodelFacts {

    private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    private val logger: Logger = loggerFor<MaterializationMetamodelFactGatherer>()

    override fun invoke(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): MaterializationMetamodelFacts {
        logger.info(
            """gather_facts_on_materialization_metamodel: 
            |[ feature_engineering_model.query_object_type.name: {}, 
            |graphql_schema.query_type.name: {} ]"""
                .flatten(),
            featureEngineeringModel.typeDefinitionRegistry
                .getType("Query")
                .toOption()
                .map(TypeDefinition<*>::getName)
                .getOrElse { "<NA>" },
            graphQLSchema.queryType.name
        )
        return graphQLSchema.queryType
            .toOption()
            .flatMap(calculateCanonicalPathsOffQueryObjectType(graphQLSchema))
            .map(calculateDomainSpecifiedDataElementSources(featureEngineeringModel, graphQLSchema))
            .map(
                calculateFeatureSpecifiedFeatureCalculators(featureEngineeringModel, graphQLSchema)
            )
            .getOrElse { DefaultMaterializationMetamodelFacts.empty() }
            .also { mmf: MaterializationMetamodelFacts ->
                logger.debug(
                    "paths: {}",
                    mmf.querySchemaElementsByCanonicalPath
                        .asSequence()
                        .sortedBy(Map.Entry<GQLOperationPath, GraphQLSchemaElement>::key)
                        .joinToString(",\n") { (p: GQLOperationPath, e: GraphQLSchemaElement) ->
                            val elementString =
                                e.toOption()
                                    .filterIsInstance<GraphQLNamedSchemaElement>()
                                    .map(GraphQLNamedSchemaElement::getName)
                                    .zip(e.run { this::class }.simpleName.toOption()) { n, t ->
                                        "$n:$t"
                                    }
                                    .orElse { e.run { this::class }.qualifiedName.toOption() }
                                    .getOrElse { "<NA>" }
                            "${p.toDecodedURIString()}: $elementString"
                        }
                )
                logger.debug(
                    "coordinates: {}",
                    mmf.fieldCoordinatesByCanonicalPath
                        .asSequence()
                        .sortedBy(Map.Entry<GQLOperationPath, FieldCoordinates>::key)
                        .joinToString(",\n") { (p: GQLOperationPath, fc: FieldCoordinates) ->
                            "${p.toDecodedURIString()}: $fc"
                        }
                )
                logger.debug(
                    "paths: {}",
                    mmf.canonicalPathsByFieldCoordinates
                        .asSequence()
                        .sortedWith(
                            Comparator.comparing(
                                Map.Entry<FieldCoordinates, GQLOperationPath>::key,
                                Comparator.comparing(FieldCoordinates::getTypeName)
                                    .thenComparing(FieldCoordinates::getFieldName)
                            )
                        )
                        .joinToString(",\n") { (fc: FieldCoordinates, p: GQLOperationPath) ->
                            "${fc}: ${p.toDecodedURIString()}"
                        }
                )
            }
    }

    private fun calculateCanonicalPathsOffQueryObjectType(
        graphQLSchema: GraphQLSchema
    ): (GraphQLObjectType) -> Option<MaterializationMetamodelFacts> {
        return { got: GraphQLObjectType ->
            val backRefQueue: Deque<Pair<GQLOperationPath, GraphQLFieldsContainer>> = LinkedList()
            Traverser.breadthFirst(
                    factGatheringTraversalFunction(graphQLSchema),
                    null,
                    DefaultMaterializationMetamodelFacts.empty()
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
                .filterIsInstance<MaterializationMetamodelFacts>()
            // .map { firstRoundMmf: MaterializationMetamodelFacts ->
            //    backRefQueue.asSequence().fold(firstRoundMmf) {
            //        mmf: MaterializationMetamodelFacts,
            //        (p: GQLOperationPath, gfc: GraphQLFieldsContainer) ->
            //        Traverser.breadthFirst(
            //                factGatheringTraversalFunction(graphQLSchema),
            //                p,
            //                mmf
            //            )
            //            .traverse(
            //                gfc,
            //                SchemaElementTraverserVisitor(
            //                    FactGatheringElementVisitor(LinkedList())
            //                )
            //            )
            //            .toOption()
            //            .mapNotNull(TraverserResult::getAccumulatedResult)
            //            .filterIsInstance<MaterializationMetamodelFacts>()
            //            .getOrElse { mmf }
            //    }
            // }
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
            when {
                node.name == QUERY_OBJECT_TYPE_NAME -> {
                    val mmf: MaterializationMetamodelFacts =
                        context.getCurrentAccumulate<MaterializationMetamodelFacts>().update {
                            putGraphQLSchemaElementForPath(GQLOperationPath.getRootPath(), node)
                        }
                    context.setVar(GQLOperationPath::class.java, GQLOperationPath.getRootPath())
                    context.setAccumulate(mmf)
                }
            }
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
                    val mmf: MaterializationMetamodelFacts =
                        context.getCurrentAccumulate<MaterializationMetamodelFacts>().update {
                            addChildPathForParentPath(parentPath, p)
                            putGraphQLSchemaElementForPath(p, node)
                            putFieldCoordinatesForPath(
                                p,
                                FieldCoordinates.coordinates(parentNode.name, node.name)
                            )
                            putPathForFieldCoordinates(
                                FieldCoordinates.coordinates(parentNode.name, node.name),
                                p
                            )
                        }
                    context.setAccumulate(mmf)
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
                                val mmf: MaterializationMetamodelFacts =
                                    context
                                        .getCurrentAccumulate<MaterializationMetamodelFacts>()
                                        .update {
                                            addChildPathForParentPath(parentPath, p)
                                            putGraphQLSchemaElementForPath(p, node)
                                            putFieldCoordinatesForPath(
                                                p,
                                                FieldCoordinates.coordinates(
                                                    parentNode.name,
                                                    node.name
                                                )
                                            )
                                            putPathForFieldCoordinates(
                                                FieldCoordinates.coordinates(
                                                    parentNode.name,
                                                    node.name
                                                ),
                                                p
                                            )
                                        }
                                context.setAccumulate(mmf)
                                context.setVar(GQLOperationPath::class.java, p)
                            } else {
                                // Only add field_coordinates for object_type parent if grandparent
                                // is interface_type
                                val p: GQLOperationPath =
                                    parentPath.transform { appendField(node.name) }
                                val mmf: MaterializationMetamodelFacts =
                                    context
                                        .getCurrentAccumulate<MaterializationMetamodelFacts>()
                                        .update {
                                            addChildPathForParentPath(parentPath, p)
                                            putFieldCoordinatesForPath(
                                                p,
                                                FieldCoordinates.coordinates(
                                                    parentNode.name,
                                                    node.name
                                                )
                                            )
                                            putPathForFieldCoordinates(
                                                FieldCoordinates.coordinates(
                                                    parentNode.name,
                                                    node.name
                                                ),
                                                p
                                            )
                                        }
                                context.setAccumulate(mmf)
                                context.setVar(GQLOperationPath::class.java, p)
                            }
                        }
                        else -> {
                            val p: GQLOperationPath =
                                parentPath.transform { appendField(node.name) }
                            val mmf: MaterializationMetamodelFacts =
                                context
                                    .getCurrentAccumulate<MaterializationMetamodelFacts>()
                                    .update {
                                        addChildPathForParentPath(parentPath, p)
                                        putGraphQLSchemaElementForPath(p, node)
                                        putFieldCoordinatesForPath(
                                            p,
                                            FieldCoordinates.coordinates(parentNode.name, node.name)
                                        )
                                        putPathForFieldCoordinates(
                                            FieldCoordinates.coordinates(
                                                parentNode.name,
                                                node.name
                                            ),
                                            p
                                        )
                                    }
                            context.setAccumulate(mmf)
                            context.setVar(GQLOperationPath::class.java, p)
                        }
                    }
                }
            }

            return TraversalControl.CONTINUE
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
            val mmf: MaterializationMetamodelFacts =
                context.getCurrentAccumulate<MaterializationMetamodelFacts>().update {
                    addChildPathForParentPath(parentPath, p)
                    putGraphQLSchemaElementForPath(p, node)
                }
            context.setAccumulate(mmf)
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
            val mmf: MaterializationMetamodelFacts =
                context.getCurrentAccumulate<MaterializationMetamodelFacts>().update {
                    addChildPathForParentPath(parentPath, p)
                    putGraphQLSchemaElementForPath(p, node)
                }
            context.setAccumulate(mmf)
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

    private fun calculateDomainSpecifiedDataElementSources(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): (MaterializationMetamodelFacts) -> MaterializationMetamodelFacts {
        return { mmf: MaterializationMetamodelFacts ->
            mmf.update {
                DomainSpecifiedDataElementSourceCreator.invoke(
                        featureEngineeringModel,
                        graphQLSchema
                    )
                    .fold(this) {
                        b: MaterializationMetamodelFacts.Builder,
                        dsdes: DomainSpecifiedDataElementSource ->
                        b.putDomainSpecifiedDataElementSourceForPath(dsdes.domainPath, dsdes)
                    }
            }
        }
    }

    private fun calculateFeatureSpecifiedFeatureCalculators(
        featureEngineeringModel: FeatureEngineeringModel,
        graphQLSchema: GraphQLSchema
    ): (MaterializationMetamodelFacts) -> MaterializationMetamodelFacts {
        return { mmf: MaterializationMetamodelFacts ->
            mmf.update {
                FeatureSpecifiedFeatureCalculatorCreator.invoke(
                        featureEngineeringModel,
                        graphQLSchema
                    )
                    .fold(this) {
                        b: MaterializationMetamodelFacts.Builder,
                        fsfc: FeatureSpecifiedFeatureCalculator ->
                        b.putFeatureSpecifiedFeatureCalculatorForPath(fsfc.featurePath, fsfc)
                    }
            }
        }
    }
}
