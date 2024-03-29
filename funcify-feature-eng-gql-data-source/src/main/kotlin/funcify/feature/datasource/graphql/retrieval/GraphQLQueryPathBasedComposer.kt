package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.NumericNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import graphql.language.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

object GraphQLQueryPathBasedComposer {

    private val logger: Logger = loggerFor<GraphQLQueryPathBasedComposer>()

    private val queryOperationDefinitionComposerMemoizer:
        (ImmutableSet<SchematicPath>) -> ((
                ImmutableMap<SchematicPath, JsonNode>
            ) -> OperationDefinition) by lazy {
        val cache:
            ConcurrentMap<
                ImmutableSet<SchematicPath>,
                (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition
            > =
            ConcurrentHashMap();
        { graphQLSourcePathsSet ->
            cache.computeIfAbsent(graphQLSourcePathsSet, graphQLQueryComposerCalculator())
        }
    }

    fun createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
        graphQLSourcePaths: ImmutableSet<SchematicPath>
    ): (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition {
        return queryOperationDefinitionComposerMemoizer(graphQLSourcePaths)
    }

    private fun graphQLQueryComposerCalculator():
        (ImmutableSet<SchematicPath>) -> ((
                ImmutableMap<SchematicPath, JsonNode>
            ) -> OperationDefinition) {
        return { graphQLSourcePaths: ImmutableSet<SchematicPath> ->
            val sourceAttributePathsSet: PersistentSet<SchematicPath> =
                extractAllSourceAttributePathsFromInputPathSet(graphQLSourcePaths)
            val sourceAttributesOnlyOperationDefinition: OperationDefinition =
                createSourceAttributesOnlyOperationDefinition(sourceAttributePathsSet)
            createQueryComposerFunction(
                sourceAttributePathsSet,
                sourceAttributesOnlyOperationDefinition
            )
        }
    }

    private fun extractAllSourceAttributePathsFromInputPathSet(
        graphQLSourcePaths: ImmutableSet<SchematicPath>
    ): PersistentSet<SchematicPath> {
        return graphQLSourcePaths
            .asSequence()
            .filter { sp ->
                sp.pathSegments.size >= 1 && sp.arguments.isEmpty() && sp.directives.isEmpty()
            }
            .fold(persistentSetOf()) { sourceAttributePathSet, sourceIndexPath ->
                if (sourceIndexPath in sourceAttributePathSet) {
                    sourceAttributePathSet
                } else {
                    var currentPath: SchematicPath = sourceIndexPath
                    val setBuilder: PersistentSet.Builder<SchematicPath> =
                        sourceAttributePathSet.builder()
                    while (!currentPath.isRoot() && currentPath !in sourceAttributePathSet) {
                        setBuilder.add(currentPath)
                        currentPath = currentPath.transform { dropPathSegment() }
                    }
                    setBuilder.build()
                }
            }
    }

    private fun createSourceAttributesOnlyOperationDefinition(
        graphQLSourcePaths: ImmutableSet<SchematicPath>
    ): OperationDefinition {
        return graphQLSourcePaths.asSequence().sorted().fold(
            OperationDefinition.newOperationDefinition()
                .operation(OperationDefinition.Operation.QUERY)
                .build()
        ) { opDef, sourceAttributePath ->
            SourceAttributesQueryCompositionContext(
                    operationDefinition = opDef,
                    pathSegments = LinkedList(sourceAttributePath.pathSegments)
                )
                .some()
                .recurse { ctx -> createFieldsInContextForSourceAttributePathSegments(ctx) }
                .getOrElse { opDef }
        }
    }

    private fun createQueryComposerFunction(
        sourceAttributePathsSet: PersistentSet<SchematicPath>,
        sourceAttributesOnlyOperationDefinition: OperationDefinition,
    ): (ImmutableMap<SchematicPath, JsonNode>) -> OperationDefinition {
        return { parameterValuesByVertexPath: ImmutableMap<SchematicPath, JsonNode> ->
            parameterValuesByVertexPath
                .asSequence()
                .filter { (p, _) ->
                    p.arguments.size == 1 &&
                        /*
                         * Check that parameter_path does not introduce new query branches
                         * but remains on the path of one of the source_attribute paths
                         */
                        p.getParentPath()
                            .recurse { pp ->
                                when {
                                    pp.arguments.isNotEmpty() ->
                                        pp.getParentPath().map { ppp -> ppp.left() }
                                    else -> pp.right().some()
                                }
                            }
                            .filter { ancestorPath -> ancestorPath in sourceAttributePathsSet }
                            .isDefined()
                }
                .sortedBy { (sp, _) -> sp }
                .fold(sourceAttributesOnlyOperationDefinition) {
                    opDef: OperationDefinition,
                    (parameterAttributePath: SchematicPath, parameterAttributeValue: JsonNode) ->
                    parameterAttributePath.arguments
                        .asSequence()
                        .firstOrNull()
                        .toOption()
                        .map { (argName, _) -> argName to parameterAttributeValue }
                        .map { keyValuePair ->
                            ParameterAttributeQueryCompositionContext(
                                opDef,
                                LinkedList(parameterAttributePath.pathSegments),
                                keyValuePair
                            )
                        }
                        .recurse { ctx ->
                            createFieldArgumentInContextForParameterAttributePath(ctx)
                        }
                        .getOrElse { opDef }
                }
        }
    }

    private data class SourceAttributesQueryCompositionContext(
        val operationDefinition: OperationDefinition,
        val pathSegments: LinkedList<String>,
        val selectionSet: SelectionSet =
            operationDefinition.selectionSet ?: SelectionSet.newSelectionSet().build(),
        val lineageComposer: (Field) -> OperationDefinition = { f: Field ->
            operationDefinition.transform { opBldr ->
                opBldr.selectionSet(
                    operationDefinition
                        .toOption()
                        .mapNotNull { opDef -> opDef.selectionSet }
                        .map { ss ->
                            ss.selections.asSequence().filterNot { s ->
                                s is Field && s.name == f.name
                            }
                        }
                        .map { ssSeq ->
                            SelectionSet.newSelectionSet(
                                    ssSeq
                                        .plus(f)
                                        .sortedBy { s ->
                                            s.some()
                                                .filterIsInstance<Field>()
                                                .map(Field::getName)
                                                .orNull()
                                                ?: ""
                                        }
                                        .toList()
                                )
                                .build()
                        }
                        .getOrElse { SelectionSet.newSelectionSet().selection(f).build() }
                )
            }
        }
    )

    private fun createFieldsInContextForSourceAttributePathSegments(
        context: SourceAttributesQueryCompositionContext
    ): Option<Either<SourceAttributesQueryCompositionContext, OperationDefinition>> {
        return when {
            context.pathSegments.size > 1 -> {
                val headPathSegment: String = context.pathSegments.pollFirst()
                val selectionSet: SelectionSet = context.selectionSet
                val currentFieldRef =
                    selectionSet.selections
                        .asSequence()
                        .filterIsInstance<Field>()
                        .filter { f -> f.name == headPathSegment }
                        .firstOrNull()
                        .toOption()
                val nextSelectionSet: SelectionSet =
                    currentFieldRef
                        .mapNotNull { s -> s.selectionSet }
                        .fold(SelectionSet.newSelectionSet()::build, ::identity)
                if (currentFieldRef.isDefined()) {
                    val updatedLineageComposer =
                        context.lineageComposer.compose<Field, Field, OperationDefinition> {
                            f: Field ->
                            currentFieldRef.orNull()!!.transform { fBldr ->
                                fBldr.selectionSet(
                                    SelectionSet.newSelectionSet(
                                            nextSelectionSet.selections
                                                .asSequence()
                                                .filterNot { s -> s is Field && s.name == f.name }
                                                .plus(f)
                                                .sortedBy { s ->
                                                    s.some()
                                                        .filterIsInstance<Field>()
                                                        .map(Field::getName)
                                                        .orNull()
                                                        ?: ""
                                                }
                                                .toList()
                                        )
                                        .build()
                                )
                            }
                        }
                    context
                        .copy(
                            selectionSet = nextSelectionSet,
                            lineageComposer = updatedLineageComposer
                        )
                        .left()
                        .some()
                } else {
                    val updatedLineageComposer =
                        context.lineageComposer.compose<Field, Field, OperationDefinition> {
                            f: Field ->
                            Field.newField(headPathSegment)
                                .selectionSet(
                                    SelectionSet.newSelectionSet(
                                            nextSelectionSet.selections
                                                .asSequence()
                                                .filterNot { s -> s is Field && s.name == f.name }
                                                .plus(f)
                                                .sortedBy { s ->
                                                    s.some()
                                                        .filterIsInstance<Field>()
                                                        .map(Field::getName)
                                                        .orNull()
                                                        ?: ""
                                                }
                                                .toList()
                                        )
                                        .build()
                                )
                                .build()
                        }
                    context
                        .copy(
                            selectionSet = nextSelectionSet,
                            lineageComposer = updatedLineageComposer
                        )
                        .left()
                        .some()
                }
            }
            else -> {
                val headPathSegment: String = context.pathSegments.pollFirst()
                val selectionSet: SelectionSet = context.selectionSet
                val currentFieldRef =
                    selectionSet.selections
                        .asSequence()
                        .filterIsInstance<Field>()
                        .filter { f -> f.name == headPathSegment }
                        .firstOrNull()
                        .toOption()
                context.lineageComposer
                    .invoke(currentFieldRef.getOrElse { Field.newField(headPathSegment).build() })
                    .right()
                    .some()
            }
        }
    }

    private data class ParameterAttributeQueryCompositionContext(
        val operationDefinition: OperationDefinition,
        val pathSegments: LinkedList<String>,
        val nameJsonNodePair: Pair<String, JsonNode>,
        val selectionSet: SelectionSet =
            operationDefinition.selectionSet ?: SelectionSet.newSelectionSet().build(),
        val lineageComposer: (Field) -> OperationDefinition = { f: Field ->
            operationDefinition.transform { opBldr ->
                opBldr.selectionSet(
                    operationDefinition
                        .toOption()
                        .mapNotNull { opDef -> opDef.selectionSet }
                        .map { ss ->
                            ss.selections.asSequence().filterNot { s ->
                                s is Field && s.name == f.name
                            }
                        }
                        .map { ssSeq ->
                            SelectionSet.newSelectionSet(
                                    ssSeq
                                        .plus(f)
                                        .sortedBy { s ->
                                            s.some()
                                                .filterIsInstance<Field>()
                                                .map(Field::getName)
                                                .orNull()
                                                ?: ""
                                        }
                                        .toList()
                                )
                                .build()
                        }
                        .getOrElse { SelectionSet.newSelectionSet().selection(f).build() }
                )
            }
        }
    )

    private fun createFieldArgumentInContextForParameterAttributePath(
        context: ParameterAttributeQueryCompositionContext
    ): Option<Either<ParameterAttributeQueryCompositionContext, OperationDefinition>> {
        return when {
            context.pathSegments.size > 1 -> {
                val headPathSegment: String = context.pathSegments.pollFirst()
                val selectionSet: SelectionSet = context.selectionSet
                val currentFieldRef =
                    selectionSet.selections
                        .asSequence()
                        .filterIsInstance<Field>()
                        .filter { f -> f.name == headPathSegment }
                        .firstOrNull()
                        .toOption()
                val nextSelectionSet: SelectionSet =
                    currentFieldRef
                        .mapNotNull { s -> s.selectionSet }
                        .fold(SelectionSet.newSelectionSet()::build, ::identity)
                if (currentFieldRef.isDefined()) {
                    val updatedLineageComposer =
                        context.lineageComposer.compose<Field, Field, OperationDefinition> {
                            f: Field ->
                            currentFieldRef.orNull()!!.transform { fBldr ->
                                fBldr.selectionSet(
                                    SelectionSet.newSelectionSet(
                                            nextSelectionSet.selections
                                                .asSequence()
                                                .filterNot { s -> s is Field && s.name == f.name }
                                                .plus(f)
                                                .sortedBy { s ->
                                                    s.some()
                                                        .filterIsInstance<Field>()
                                                        .map(Field::getName)
                                                        .orNull()
                                                        ?: ""
                                                }
                                                .toList()
                                        )
                                        .build()
                                )
                            }
                        }
                    context
                        .copy(
                            selectionSet = nextSelectionSet,
                            lineageComposer = updatedLineageComposer
                        )
                        .left()
                        .some()
                } else {
                    val updatedLineageComposer =
                        context.lineageComposer.compose<Field, Field, OperationDefinition> {
                            f: Field ->
                            Field.newField(headPathSegment)
                                .selectionSet(
                                    SelectionSet.newSelectionSet(
                                            nextSelectionSet.selections
                                                .asSequence()
                                                .filterNot { s -> s is Field && s.name == f.name }
                                                .plus(f)
                                                .sortedBy { s ->
                                                    s.some()
                                                        .filterIsInstance<Field>()
                                                        .map(Field::getName)
                                                        .orNull()
                                                        ?: ""
                                                }
                                                .toList()
                                        )
                                        .build()
                                )
                                .build()
                        }
                    context
                        .copy(
                            selectionSet = nextSelectionSet,
                            lineageComposer = updatedLineageComposer
                        )
                        .left()
                        .some()
                }
            }
            context.pathSegments.size == 1 -> {
                val headPathSegment: String = context.pathSegments.pollFirst()
                val selectionSet: SelectionSet = context.selectionSet
                val updatedField: Field =
                    selectionSet.selections
                        .asSequence()
                        .filterIsInstance<Field>()
                        .filter { f -> f.name == headPathSegment }
                        .firstOrNull()
                        .toOption()
                        .fold(
                            {
                                Field.newField(headPathSegment)
                                    .arguments(
                                        listOf(
                                            Argument(
                                                context.nameJsonNodePair.first,
                                                convertJsonNodeToGraphQLValue(
                                                    context.nameJsonNodePair.second
                                                )
                                            )
                                        )
                                    )
                                    .build()
                            },
                            { f: Field ->
                                f.transform { fBldr ->
                                    fBldr.arguments(
                                        f.arguments
                                            .asSequence()
                                            .filterNot { a ->
                                                a.name == context.nameJsonNodePair.first
                                            }
                                            .plus(
                                                Argument(
                                                    context.nameJsonNodePair.first,
                                                    convertJsonNodeToGraphQLValue(
                                                        context.nameJsonNodePair.second
                                                    )
                                                )
                                            )
                                            .sortedBy { a -> a.name }
                                            .toList()
                                    )
                                }
                            }
                        )
                context.lineageComposer.invoke(updatedField).right().some()
            }
            else -> {
                context.operationDefinition.right().some()
            }
        }
    }

    // Potentially recursive call, if this is an array or object node value
    private fun convertJsonNodeToGraphQLValue(jsonNode: JsonNode): Value<*> {
        return when (jsonNode.nodeType) {
            JsonNodeType.BINARY -> IntValue.of(jsonNode.asInt(0))
            JsonNodeType.BOOLEAN -> BooleanValue.of(jsonNode.asBoolean(false))
            JsonNodeType.NUMBER -> {
                when ((jsonNode as NumericNode).numberType()) {
                    JsonParser.NumberType.INT -> IntValue.of(jsonNode.asInt(0))
                    JsonParser.NumberType.LONG ->
                        IntValue.newIntValue(jsonNode.bigIntegerValue()).build()
                    JsonParser.NumberType.BIG_INTEGER ->
                        IntValue.newIntValue(jsonNode.bigIntegerValue()).build()
                    JsonParser.NumberType.FLOAT -> FloatValue.of(jsonNode.asDouble(0.0))
                    JsonParser.NumberType.DOUBLE -> FloatValue.of(jsonNode.asDouble(0.0))
                    JsonParser.NumberType.BIG_DECIMAL ->
                        FloatValue.newFloatValue(jsonNode.decimalValue()).build()
                    else -> NullValue.of()
                }
            }
            JsonNodeType.STRING -> StringValue.of(jsonNode.asText(""))
            JsonNodeType.OBJECT -> {
                ObjectValue.newObjectValue()
                    .objectFields(
                        jsonNode
                            .fields()
                            .asSequence()
                            .map { (key, value) ->
                                ObjectField.newObjectField()
                                    .name(key)
                                    .value(convertJsonNodeToGraphQLValue(value))
                                    .build()
                            }
                            .sortedBy { of -> of.name }
                            .toList()
                    )
                    .build()
            }
            JsonNodeType.ARRAY -> {
                ArrayValue.newArrayValue()
                    .values(
                        jsonNode
                            .asSequence()
                            .map { value -> convertJsonNodeToGraphQLValue(value) }
                            .toList()
                    )
                    .build()
            }
            else -> NullValue.of()
        }
    }
}
